package com.github.knokko.boiler.builders.device;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDevice;

import java.util.Set;

@FunctionalInterface
public interface VkDeviceCreator {

	VkDevice vkCreateDevice(
			VkDeviceCreateInfo ciDevice,
			Set<String> enabledInstanceExtensions,
			VkPhysicalDevice physicalDevice,
			MemoryStack stack
	);
}
