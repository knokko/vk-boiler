package com.github.knokko.boiler.builders.device;

import com.github.knokko.boiler.memory.callbacks.VkbAllocationCallbacks;
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
			VkbAllocationCallbacks allocationCallbacks,
			MemoryStack stack
	);
}
