package com.github.knokko.boiler.builders.instance;

import com.github.knokko.boiler.memory.callbacks.VkbAllocationCallbacks;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;

@FunctionalInterface
public interface VkInstanceCreator {

	VkInstance vkCreateInstance(
			VkInstanceCreateInfo ciInstance,
			VkbAllocationCallbacks allocationCallbacks,
			MemoryStack stack
	);
}
