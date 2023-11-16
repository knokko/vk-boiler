package com.github.knokko.boiler.builder.device;

import org.lwjgl.vulkan.VkPhysicalDeviceVulkan13Features;

@FunctionalInterface
public interface RequiredFeatures13 {

    boolean supportsRequiredFeatures(VkPhysicalDeviceVulkan13Features supportedFeatures);
}
