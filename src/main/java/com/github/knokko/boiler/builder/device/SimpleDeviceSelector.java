package com.github.knokko.boiler.builder.device;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR;
import static org.lwjgl.vulkan.VK10.*;

public record SimpleDeviceSelector(int... preferredDeviceTypes) implements PhysicalDeviceSelector {

    static Set<String> getSupportedDeviceExtensions(
            MemoryStack stack, VkPhysicalDevice vkPhysicalDevice, String context
    ) {
        var pNumExtensions = stack.callocInt(1);
        assertVkSuccess(vkEnumerateDeviceExtensionProperties(
                vkPhysicalDevice, (ByteBuffer) null, pNumExtensions, null
        ), "EnumerateDeviceExtensionProperties", context + " count");
        int numExtensions = pNumExtensions.get(0);

        var pExtensions = VkExtensionProperties.calloc(numExtensions, stack);
        assertVkSuccess(vkEnumerateDeviceExtensionProperties(
                vkPhysicalDevice, (ByteBuffer) null, pNumExtensions, pExtensions
        ), "EnumerateDeviceExtensionProperties", context);

        var extensions = new HashSet<String>(numExtensions);
        for (int index = 0; index < numExtensions; index++) {
            extensions.add(pExtensions.get(index).extensionNameString());
        }
        return extensions;
    }

    @Override
    public VkPhysicalDevice choosePhysicalDevice(
            MemoryStack stack, VkInstance vkInstance,
            long windowSurface, Set<String> requiredDeviceExtensions
    ) {
        var pNumDevices = stack.callocInt(1);
        assertVkSuccess(vkEnumeratePhysicalDevices(
                vkInstance, pNumDevices, null
        ), "EnumeratePhysicalDevices", "SimpleDeviceSelector count");
        int numDevices = pNumDevices.get(0);

        var pDevices = stack.callocPointer(numDevices);
        assertVkSuccess(vkEnumeratePhysicalDevices(
                vkInstance, pNumDevices, pDevices
        ), "EnumeratePhysicalDevices", "SimpleDeviceSelector devices");

        var devices = new ArrayList<VkPhysicalDevice>(numDevices);
        var deviceProperties = new ArrayList<VkPhysicalDeviceProperties>(numDevices);

        deviceLoop:
        for (int index = 0; index < numDevices; index++) {
            var device = new VkPhysicalDevice(pDevices.get(index), vkInstance);
            var properties = VkPhysicalDeviceProperties.calloc(stack);
            vkGetPhysicalDeviceProperties(device, properties);

            var supportedExtensions = getSupportedDeviceExtensions(stack, device, "DeviceSelector");
            for (String extension : requiredDeviceExtensions) {
                if (!supportedExtensions.contains(extension)) continue deviceLoop;
            }

            boolean hasPresentQueueFamily = false;
            boolean hasGraphicsQueueFamily = false;

            var pNumQueueFamilies = stack.callocInt(1);
            vkGetPhysicalDeviceQueueFamilyProperties(device, pNumQueueFamilies, null);
            int numQueueFamilies = pNumQueueFamilies.get(0);
            var pQueueFamilies = VkQueueFamilyProperties.calloc(numQueueFamilies, stack);
            vkGetPhysicalDeviceQueueFamilyProperties(device, pNumQueueFamilies, pQueueFamilies);

            for (int queueFamilyIndex = 0; queueFamilyIndex < numQueueFamilies; queueFamilyIndex++) {
                if (windowSurface != 0L) {
                    var pPresentSupport = stack.callocInt(1);
                    assertVkSuccess(vkGetPhysicalDeviceSurfaceSupportKHR(
                            device, queueFamilyIndex, windowSurface, pPresentSupport
                    ), "GetPhysicalDeviceSurfaceSupportKHR", "SimpleDeviceSelector");
                    if (pPresentSupport.get(0) == VK_TRUE) hasPresentQueueFamily = true;
                } else hasPresentQueueFamily = true;
                if ((pQueueFamilies.get(queueFamilyIndex).queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) hasGraphicsQueueFamily = true;
            }

            if (!hasPresentQueueFamily || !hasGraphicsQueueFamily) continue;

            devices.add(device);
            deviceProperties.add(properties);
        }

        if (devices.isEmpty()) return null;

        for (int preferredType : preferredDeviceTypes) {
            for (int index = 0; index < deviceProperties.size(); index++) {
                if (deviceProperties.get(index).deviceType() == preferredType) {
                    return devices.get(index);
                }
            }
        }

        return devices.get(0);
    }
}
