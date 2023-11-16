package com.github.knokko.boiler.builder.device;

import org.lwjgl.vulkan.VkPhysicalDeviceVulkan12Features;

@FunctionalInterface
public interface RequiredFeatures12 {

    boolean supportsRequiredFeatures(VkPhysicalDeviceVulkan12Features supportedFeatures);
}
