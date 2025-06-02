package com.github.knokko.boiler.builders.device;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPhysicalDeviceVulkan14Features;

@FunctionalInterface
public interface FeaturePicker14 {

	void enableFeatures(
			MemoryStack stack,
			VkPhysicalDeviceVulkan14Features supportedFeatures,
			VkPhysicalDeviceVulkan14Features toEnable
	);
}
