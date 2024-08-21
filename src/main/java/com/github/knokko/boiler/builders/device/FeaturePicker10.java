package com.github.knokko.boiler.builders.device;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures;

@FunctionalInterface
public interface FeaturePicker10 {

	void enableFeatures(MemoryStack stack, VkPhysicalDeviceFeatures supportedFeatures, VkPhysicalDeviceFeatures toEnable);
}
