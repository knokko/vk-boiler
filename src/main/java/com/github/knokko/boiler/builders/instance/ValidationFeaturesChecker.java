package com.github.knokko.boiler.builders.instance;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.*;
import static org.lwjgl.vulkan.VK12.VK_API_VERSION_1_2;

public class ValidationFeaturesChecker {

    private final VkInstance vkInstance;
    public final boolean gpuAssistedValidation, debugPrintf;

    public ValidationFeaturesChecker(MemoryStack stack) {
        if (VK.getInstanceVersionSupported() < VK_API_VERSION_1_2) {
            this.vkInstance = null;
            this.gpuAssistedValidation = false;
            this.debugPrintf = false;
            return;
        }

        var appInfo = VkApplicationInfo.calloc(stack);
        appInfo.sType$Default();
        appInfo.apiVersion(VK_API_VERSION_1_2);

        var ciInstance = VkInstanceCreateInfo.calloc(stack);
        ciInstance.sType$Default();
        ciInstance.pApplicationInfo(appInfo);

        var pInstance = stack.callocPointer(1);
        assertVkSuccess(vkCreateInstance(
                ciInstance, null, pInstance
        ), "CreateInstance", "validation feature check");
        this.vkInstance = new VkInstance(pInstance.get(), ciInstance);

        var pNumDevices = stack.callocInt(1);
        assertVkSuccess(vkEnumeratePhysicalDevices(
                vkInstance, pNumDevices, null
        ), "EnumeratePhysicalDevices", "validation feature check count");
        int numDevices = pNumDevices.get(0);

        var pDevices = stack.callocPointer(numDevices);
        assertVkSuccess(vkEnumeratePhysicalDevices(
                vkInstance, pNumDevices, pDevices
        ), "EnumeratePhysicalDevices", "validation feature check count");

        boolean supportsGpuAv = true;
        boolean supportsPrint = true;

        var features = VkPhysicalDeviceFeatures2.calloc(stack);
        features.sType$Default();
        var features12 = VkPhysicalDeviceVulkan12Features.calloc(stack);
        features12.sType$Default();
        features.pNext(features12);

        for (int index = 0; index < numDevices; index++) {
            var device = new VkPhysicalDevice(pDevices.get(index), vkInstance);
            vkGetPhysicalDeviceFeatures2(device, features);

            supportsGpuAv &= features12.bufferDeviceAddress();
            supportsPrint &= features.features().fragmentStoresAndAtomics() &&
                    features.features().vertexPipelineStoresAndAtomics() && features12.timelineSemaphore();
        }

        this.gpuAssistedValidation = supportsGpuAv;
        this.debugPrintf = supportsPrint;
    }

    public void destroy() {
        if (vkInstance != null) vkDestroyInstance(vkInstance, null);
    }
}
