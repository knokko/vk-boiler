package com.github.knokko.boiler.builder;

import com.github.knokko.boiler.exceptions.NoVkPhysicalDeviceException;
import com.github.knokko.boiler.queue.BoilerQueue;
import com.github.knokko.boiler.queue.QueueFamilies;
import com.github.knokko.boiler.queue.QueueFamily;
import com.github.knokko.boiler.util.CollectionHelper;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocatorCreateInfo;
import org.lwjgl.util.vma.VmaVulkanFunctions;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.util.*;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVmaSuccess;
import static org.lwjgl.glfw.GLFWVulkan.glfwCreateWindowSurface;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.EXTMemoryBudget.VK_EXT_MEMORY_BUDGET_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRBindMemory2.VK_KHR_BIND_MEMORY_2_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRDedicatedAllocation.VK_KHR_DEDICATED_ALLOCATION_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRGetMemoryRequirements2.VK_KHR_GET_MEMORY_REQUIREMENTS_2_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR;
import static org.lwjgl.vulkan.VK10.*;

class BoilerDeviceBuilder {

    static Result createDevice(BoilerBuilder builder, BoilerInstanceBuilder.Result instanceResult) {
        VkPhysicalDevice vkPhysicalDevice;
        VkDevice vkDevice;
        Set<String> enabledExtensions;
        long[] windowSurfaces;
        QueueFamilies queueFamilies;
        long vmaAllocator;

        try (var stack = stackPush()) {
            var pSurface = stack.callocLong(1);
            windowSurfaces = builder.windows.stream().mapToLong(windowBuilder -> {
                assertVkSuccess(glfwCreateWindowSurface(
                        instanceResult.vkInstance(), windowBuilder.glfwWindow, null, pSurface
                ), "glfwCreateWindowSurface", null);
                return pSurface.get(0);
            }).toArray();

            VkPhysicalDevice[] candidateDevices = BasicDeviceFilter.getCandidates(
                    builder, instanceResult.vkInstance(), windowSurfaces, builder.printDeviceRejectionInfo
            );
            if (candidateDevices.length == 0) throw new NoVkPhysicalDeviceException();

            vkPhysicalDevice = builder.deviceSelector.choosePhysicalDevice(
                    stack, candidateDevices, instanceResult.vkInstance()
            );
            if (vkPhysicalDevice == null) throw new NoVkPhysicalDeviceException();
        }

        try (var stack = stackPush()) {
            var pNumSupportedExtensions = stack.callocInt(1);
            assertVkSuccess(vkEnumerateDeviceExtensionProperties(
                    vkPhysicalDevice, (ByteBuffer) null, pNumSupportedExtensions, null
            ), "EnumerateDeviceExtensionProperties", "BoilerDeviceBuilder count");
            int numSupportedExtensions = pNumSupportedExtensions.get(0);

            var pSupportedExtensions = VkExtensionProperties.calloc(numSupportedExtensions, stack);
            assertVkSuccess(vkEnumerateDeviceExtensionProperties(
                    vkPhysicalDevice, (ByteBuffer) null, pNumSupportedExtensions, pSupportedExtensions
            ), "EnumerateDeviceExtensionProperties", "BoilerDeviceBuilder extensions");

            Set<String> supportedExtensions = new HashSet<>(numSupportedExtensions);
            for (int index = 0; index < numSupportedExtensions; index++) {
                supportedExtensions.add(pSupportedExtensions.get(index).extensionNameString());
            }
            for (var extension : builder.requiredVulkanDeviceExtensions) {
                if (!supportedExtensions.contains(extension)) {
                    // This is a programming error because the physical device selector must not choose physical
                    // devices that don't support all required extensions
                    throw new Error("Chosen device doesn't support required extension " + extension);
                }
            }

            enabledExtensions = new HashSet<>(builder.requiredVulkanDeviceExtensions);
            for (var extension : builder.desiredVulkanDeviceExtensions) {
                if (supportedExtensions.contains(extension)) enabledExtensions.add(extension);
            }
        }

        QueueFamily[] presentFamilies;
        try (var stack = stackPush()) {
            if (VK_API_VERSION_MAJOR(builder.apiVersion) != 1) {
                throw new UnsupportedOperationException("Unknown api major version: " + VK_API_VERSION_MAJOR(builder.apiVersion));
            }

            var supportedFeatures = SupportedFeatures.query(
                    stack, vkPhysicalDevice, builder.apiVersion,
                    !builder.vkDeviceFeaturePicker10.isEmpty(), !builder.vkDeviceFeaturePicker11.isEmpty(),
                    !builder.vkDeviceFeaturePicker12.isEmpty(), !builder.vkDeviceFeaturePicker13.isEmpty()
            );

            int minorVersion = VK_API_VERSION_MINOR(builder.apiVersion);
            VkPhysicalDeviceFeatures enabledFeatures10;
            VkPhysicalDeviceFeatures2 enabledFeatures2 = null;
            if (minorVersion == 0) {
                enabledFeatures10 = VkPhysicalDeviceFeatures.calloc(stack);
                for (var featurePicker : builder.vkDeviceFeaturePicker10) {
                    featurePicker.enableFeatures(stack, supportedFeatures.features10(), enabledFeatures10);
                }
            } else {
                enabledFeatures2 = VkPhysicalDeviceFeatures2.calloc(stack);
                enabledFeatures2.sType$Default();
                enabledFeatures10 = enabledFeatures2.features();

                for (var featurePicker : builder.vkDeviceFeaturePicker10) {
                    featurePicker.enableFeatures(stack, supportedFeatures.features10(), enabledFeatures10);
                }
                if (!builder.vkDeviceFeaturePicker11.isEmpty()) {
                    var enabledFeatures11 = VkPhysicalDeviceVulkan11Features.calloc(stack);
                    enabledFeatures11.sType$Default();
                    for (var picker : builder.vkDeviceFeaturePicker11) {
                        picker.enableFeatures(stack, supportedFeatures.features11(), enabledFeatures11);
                    }
                    enabledFeatures2.pNext(enabledFeatures11);
                }
                if (!builder.vkDeviceFeaturePicker12.isEmpty()) {
                    var enabledFeatures12 = VkPhysicalDeviceVulkan12Features.calloc(stack);
                    enabledFeatures12.sType$Default();
                    for (var picker : builder.vkDeviceFeaturePicker12) {
                        picker.enableFeatures(stack, supportedFeatures.features12(), enabledFeatures12);
                    }
                    enabledFeatures2.pNext(enabledFeatures12);
                }
                if (!builder.vkDeviceFeaturePicker13.isEmpty()) {
                    var enabledFeatures13 = VkPhysicalDeviceVulkan13Features.calloc(stack);
                    enabledFeatures13.sType$Default();
                    for (var picker : builder.vkDeviceFeaturePicker13) {
                        picker.enableFeatures(stack, supportedFeatures.features13(), enabledFeatures13);
                    }
                    enabledFeatures2.pNext(enabledFeatures13);
                }
            }

            var pNumQueueFamilies = stack.callocInt(1);
            vkGetPhysicalDeviceQueueFamilyProperties(vkPhysicalDevice, pNumQueueFamilies, null);
            int numQueueFamilies = pNumQueueFamilies.get(0);
            var pQueueFamilies = VkQueueFamilyProperties.calloc(numQueueFamilies, stack);
            vkGetPhysicalDeviceQueueFamilyProperties(vkPhysicalDevice, pNumQueueFamilies, pQueueFamilies);

            var presentSupportMatrix = new boolean[numQueueFamilies][windowSurfaces.length];
            var pPresentSupport = stack.callocInt(1);

            for (int familyIndex = 0; familyIndex < numQueueFamilies; familyIndex++) {
                for (int surfaceIndex = 0; surfaceIndex < windowSurfaces.length; surfaceIndex++) {
                    assertVkSuccess(vkGetPhysicalDeviceSurfaceSupportKHR(
                            vkPhysicalDevice, familyIndex, windowSurfaces[surfaceIndex], pPresentSupport
                    ), "GetPhysicalDeviceSurfaceSupportKHR", "BoilerDeviceBuilder");
                    presentSupportMatrix[familyIndex][surfaceIndex] = pPresentSupport.get(0) == VK_TRUE;
                }
            }

            var queueFamilyMapping = builder.queueFamilyMapper.mapQueueFamilies(
                    pQueueFamilies, enabledExtensions, presentSupportMatrix
            );
            queueFamilyMapping.validate();

            var uniqueQueueFamilies = new HashMap<Integer, float[]>();
            // Do presentFamily first so that it will be overwritten by the others if the queue family is shared
            for (var presentFamilyIndex : queueFamilyMapping.presentFamilyIndices()) {
                uniqueQueueFamilies.put(presentFamilyIndex, new float[]{1f});
            }
            uniqueQueueFamilies.put(queueFamilyMapping.graphics().index(), queueFamilyMapping.graphics().priorities());
            uniqueQueueFamilies.put(queueFamilyMapping.compute().index(), queueFamilyMapping.compute().priorities());
            uniqueQueueFamilies.put(queueFamilyMapping.transfer().index(), queueFamilyMapping.transfer().priorities());
            if (queueFamilyMapping.videoEncode() != null) {
                uniqueQueueFamilies.put(queueFamilyMapping.videoEncode().index(), queueFamilyMapping.videoEncode().priorities());
            }
            if (queueFamilyMapping.videoDecode() != null) {
                uniqueQueueFamilies.put(queueFamilyMapping.videoDecode().index(), queueFamilyMapping.videoDecode().priorities());
            }

            var pQueueCreateInfos = VkDeviceQueueCreateInfo.calloc(uniqueQueueFamilies.size(), stack);
            int ciQueueIndex = 0;
            for (var entry : uniqueQueueFamilies.entrySet()) {
                var ciQueue = pQueueCreateInfos.get(ciQueueIndex);
                ciQueue.sType$Default();
                ciQueue.flags(0);
                ciQueue.queueFamilyIndex(entry.getKey());
                ciQueue.pQueuePriorities(stack.floats(entry.getValue()));

                ciQueueIndex += 1;
            }

            var ciDevice = VkDeviceCreateInfo.calloc(stack);
            ciDevice.sType$Default();
            if (enabledFeatures2 != null) ciDevice.pNext(enabledFeatures2.address());
            ciDevice.flags(0);
            ciDevice.pQueueCreateInfos(pQueueCreateInfos);
            ciDevice.ppEnabledLayerNames(null); // Device layers are deprecated
            ciDevice.ppEnabledExtensionNames(CollectionHelper.encodeStringSet(enabledExtensions, stack));
            if (enabledFeatures2 == null) ciDevice.pEnabledFeatures(enabledFeatures10);

            for (var preCreator : builder.preDeviceCreators) {
                preCreator.beforeDeviceCreation(ciDevice, instanceResult.enabledExtensions(), vkPhysicalDevice, stack);
            }

            vkDevice = builder.vkDeviceCreator.vkCreateDevice(
                    ciDevice, instanceResult.enabledExtensions(), vkPhysicalDevice, stack
            );

            var queueFamilyMap = new HashMap<Integer, QueueFamily>();
            for (var entry : uniqueQueueFamilies.entrySet()) {
                queueFamilyMap.put(entry.getKey(), getQueueFamily(stack, vkDevice, entry.getKey(), entry.getValue().length));
            }

            presentFamilies = new QueueFamily[windowSurfaces.length];
            for (int surfaceIndex = 0; surfaceIndex < windowSurfaces.length; surfaceIndex++) {
                presentFamilies[surfaceIndex] = queueFamilyMap.get(queueFamilyMapping.presentFamilyIndices()[surfaceIndex]);
            }

            int videoEncodeIndex = queueFamilyMapping.videoEncode() != null ? queueFamilyMapping.videoEncode().index() : -1;
            int videoDecodeIndex = queueFamilyMapping.videoDecode() != null ? queueFamilyMapping.videoDecode().index() : -1;
            queueFamilies = new QueueFamilies(
                    queueFamilyMap.get(queueFamilyMapping.graphics().index()),
                    queueFamilyMap.get(queueFamilyMapping.compute().index()),
                    queueFamilyMap.get(queueFamilyMapping.transfer().index()),
                    queueFamilyMap.get(videoEncodeIndex),
                    queueFamilyMap.get(videoDecodeIndex),
                    Collections.unmodifiableCollection(queueFamilyMap.values())
            );

            var vmaVulkanFunctions = VmaVulkanFunctions.calloc(stack);
            vmaVulkanFunctions.set(instanceResult.vkInstance(), vkDevice);

            int vmaFlags = getVmaFlags(enabledExtensions);

            var ciAllocator = VmaAllocatorCreateInfo.calloc(stack);
            ciAllocator.flags(vmaFlags);
            ciAllocator.physicalDevice(vkPhysicalDevice);
            ciAllocator.device(vkDevice);
            ciAllocator.instance(instanceResult.vkInstance());
            ciAllocator.pVulkanFunctions(vmaVulkanFunctions);
            ciAllocator.vulkanApiVersion(builder.apiVersion);

            var pAllocator = stack.callocPointer(1);

            assertVmaSuccess(vmaCreateAllocator(
                    ciAllocator, pAllocator
            ), "CreateAllocator", "BoilerDeviceBuilder");
            vmaAllocator = pAllocator.get(0);
        }

        return new Result(
                vkPhysicalDevice, vkDevice, enabledExtensions,
                windowSurfaces, presentFamilies, queueFamilies, vmaAllocator
        );
    }

    private static int getVmaFlags(Set<String> enabledExtensions) {
        int vmaFlags = 0;
        if (enabledExtensions.contains(VK_KHR_DEDICATED_ALLOCATION_EXTENSION_NAME)
                && enabledExtensions.contains(VK_KHR_GET_MEMORY_REQUIREMENTS_2_EXTENSION_NAME)
        ) {
            vmaFlags |= VMA_ALLOCATOR_CREATE_KHR_DEDICATED_ALLOCATION_BIT;
        }
        if (enabledExtensions.contains(VK_KHR_BIND_MEMORY_2_EXTENSION_NAME)) {
            vmaFlags |= VMA_ALLOCATOR_CREATE_KHR_BIND_MEMORY2_BIT;
        }
        if (enabledExtensions.contains(VK_EXT_MEMORY_BUDGET_EXTENSION_NAME)) {
            vmaFlags |= VMA_ALLOCATOR_CREATE_EXT_MEMORY_BUDGET_BIT;
        }
        return vmaFlags;
    }

    private static QueueFamily getQueueFamily(MemoryStack stack, VkDevice vkDevice, int familyIndex, int queueCount) {
        List<BoilerQueue> queues = new ArrayList<>(queueCount);
        for (int queueIndex = 0; queueIndex < queueCount; queueIndex++) {
            var pQueue = stack.callocPointer(1);
            vkGetDeviceQueue(vkDevice, familyIndex, queueIndex, pQueue);
            queues.add(new BoilerQueue(new VkQueue(pQueue.get(0), vkDevice)));
        }
        return new QueueFamily(familyIndex, Collections.unmodifiableList(queues));
    }

    record Result(
            VkPhysicalDevice vkPhysicalDevice, VkDevice vkDevice, Set<String> enabledExtensions,
            long[] windowSurfaces, QueueFamily[] presentFamilies, QueueFamilies queueFamilies, long vmaAllocator
    ) {}
}
