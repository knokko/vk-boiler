package com.github.knokko.boiler.builder.device;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPhysicalDeviceVulkan12Features;

@FunctionalInterface
public interface FeaturePicker12 {

    void enableFeatures(
            MemoryStack stack,
            VkPhysicalDeviceVulkan12Features supportedFeatures,
            VkPhysicalDeviceVulkan12Features toEnable
    );
}
