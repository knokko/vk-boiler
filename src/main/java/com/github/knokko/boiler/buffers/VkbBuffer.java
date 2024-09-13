package com.github.knokko.boiler.buffers;

import com.github.knokko.boiler.BoilerInstance;

import static org.lwjgl.util.vma.Vma.vmaDestroyBuffer;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.vkDestroyBuffer;

public sealed interface VkbBuffer permits DeviceVkbBuffer, MappedVkbBuffer {
	long vkBuffer();

	long vmaAllocation();

	long size();

	/**
	 * Creates a <i>VkbBufferRange</i> that convert a part of the buffer
	 */
	default VkbBufferRange range(long offset, long size) {
		if (offset + size > size()) throw new IllegalArgumentException(offset + " + " + size + " > " + size());
		return new VkbBufferRange(this, offset, size);
	}

	/**
	 * Creates a <i>VkbBufferRange</i> that covers the whole buffer
	 */
	default VkbBufferRange fullRange() {
		return new VkbBufferRange(this, 0, size());
	}

	/**
	 * Destroys this buffer, including its <i>VmaAllocation</i> (if applicable)
	 */
	default void destroy(BoilerInstance instance) {
		if (vmaAllocation() != VK_NULL_HANDLE) vmaDestroyBuffer(instance.vmaAllocator(), vkBuffer(), vmaAllocation());
		else vkDestroyBuffer(instance.vkDevice(), vkBuffer(), null);
	}
}
