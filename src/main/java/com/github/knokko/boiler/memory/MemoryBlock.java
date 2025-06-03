package com.github.knokko.boiler.memory;

import com.github.knokko.boiler.BoilerInstance;

import java.util.ArrayList;
import java.util.Collection;

import static org.lwjgl.util.vma.Vma.vmaFreeMemory;
import static org.lwjgl.vulkan.VK10.*;

/**
 * Represents a block/group of memory allocations, buffers, and images that were allocated at the same time, and must
 * be destroyed at the same time, using {@link #destroy(BoilerInstance)}. Use {@link MemoryCombiner} to create
 * memory blocks.
 */
public class MemoryBlock {

	final Collection<Long> vkAllocations = new ArrayList<>();
	final Collection<Long> vmaAllocations = new ArrayList<>();
	final Collection<Long> vkBuffers = new ArrayList<>();
	final Collection<Long> vkImages = new ArrayList<>();
	final Collection<Long> vkImageViews = new ArrayList<>();

	/**
	 * Destroys all the image views, images, buffers, and memory allocations in this memory block.
	 */
	public void destroy(BoilerInstance instance) {
		for (long vkImageView : vkImageViews) vkDestroyImageView(instance.vkDevice(), vkImageView, null);
		for (long vkImage : vkImages) vkDestroyImage(instance.vkDevice(), vkImage, null);
		for (long vkBuffer : vkBuffers) vkDestroyBuffer(instance.vkDevice(), vkBuffer, null);
		for (long vmaAllocation : vmaAllocations) vmaFreeMemory(instance.vmaAllocator(), vmaAllocation);
		for (long vkAllocation : vkAllocations) vkFreeMemory(instance.vkDevice(), vkAllocation, null);
	}
}
