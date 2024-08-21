package com.github.knokko.boiler.builders.device;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkPhysicalDevice;

@FunctionalInterface
public interface PhysicalDeviceSelector {

	VkPhysicalDevice choosePhysicalDevice(MemoryStack stack, VkPhysicalDevice[] candidates, VkInstance vkInstance);
}
