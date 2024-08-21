package com.github.knokko.boiler.builders.device;

import org.lwjgl.vulkan.VkPhysicalDeviceVulkan13Features;

@FunctionalInterface
public interface RequiredFeatures13 {

	boolean supportsRequiredFeatures(VkPhysicalDeviceVulkan13Features supportedFeatures);
}
