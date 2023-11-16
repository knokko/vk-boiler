package com.github.knokko.boiler.builder.device;

import org.lwjgl.vulkan.VkPhysicalDeviceVulkan11Features;

@FunctionalInterface
public interface RequiredFeatures11 {

    boolean supportsRequiredFeatures(VkPhysicalDeviceVulkan11Features supportedFeatures);
}
