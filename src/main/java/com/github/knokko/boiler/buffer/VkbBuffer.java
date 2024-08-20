package com.github.knokko.boiler.buffer;

import com.github.knokko.boiler.BoilerInstance;

import static org.lwjgl.util.vma.Vma.vmaDestroyBuffer;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.vkDestroyBuffer;

public sealed interface VkbBuffer permits DeviceVkbBuffer, MappedVkbBuffer {
	long vkBuffer();

	long vmaAllocation();

	long size();

	default void destroy(BoilerInstance instance) {
		if (vmaAllocation() != VK_NULL_HANDLE) vmaDestroyBuffer(instance.vmaAllocator(), vkBuffer(), vmaAllocation());
		else vkDestroyBuffer(instance.vkDevice(), vkBuffer(), null); // TODO Test this
	}
}
