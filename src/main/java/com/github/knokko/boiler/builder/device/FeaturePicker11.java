package com.github.knokko.boiler.builder.device;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPhysicalDeviceVulkan11Features;

@FunctionalInterface
public interface FeaturePicker11 {

	void enableFeatures(
			MemoryStack stack,
			VkPhysicalDeviceVulkan11Features supportedFeatures,
			VkPhysicalDeviceVulkan11Features toEnable
	);
}
