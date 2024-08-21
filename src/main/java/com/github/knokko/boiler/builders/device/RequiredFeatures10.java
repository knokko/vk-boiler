package com.github.knokko.boiler.builders.device;

import org.lwjgl.vulkan.VkPhysicalDeviceFeatures;

@FunctionalInterface
public interface RequiredFeatures10 {

	boolean supportsRequiredFeatures(VkPhysicalDeviceFeatures supportedFeatures);
}
