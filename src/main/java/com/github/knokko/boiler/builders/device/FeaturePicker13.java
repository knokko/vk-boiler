package com.github.knokko.boiler.builders.device;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPhysicalDeviceVulkan13Features;

@FunctionalInterface
public interface FeaturePicker13 {

	void enableFeatures(
			MemoryStack stack,
			VkPhysicalDeviceVulkan13Features supportedFeatures,
			VkPhysicalDeviceVulkan13Features toEnable
	);
}
