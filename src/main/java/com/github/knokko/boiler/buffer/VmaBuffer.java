package com.github.knokko.boiler.buffer;

import static org.lwjgl.util.vma.Vma.vmaDestroyBuffer;

public sealed interface VmaBuffer permits DeviceOnlyVmaBuffer, MappedVmaBuffer {
	long vkBuffer();

	long vmaAllocation();

	long size();

	default void destroy(long vmaAllocator) {
		vmaDestroyBuffer(vmaAllocator, vkBuffer(), vmaAllocation());
	}
}
