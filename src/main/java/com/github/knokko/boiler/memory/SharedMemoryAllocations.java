package com.github.knokko.boiler.memory;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.images.VkbImage;

import java.util.List;

import static org.lwjgl.util.vma.Vma.vmaFreeMemory;
import static org.lwjgl.vulkan.VK10.*;

/**
 * Represents a group of buffers, images, and memory allocations that were allocated at the same time, and should be
 * destroyed at the same time. Use {@link SharedMemoryBuilder} to create instances of this class.
 */
public class SharedMemoryAllocations {

	private final boolean usedVma;
	private final List<Long> vkAllocations, vkBuffers;
	private final List<VkbImage> images;

	SharedMemoryAllocations(boolean usedVma, List<Long> vkAllocations, List<Long> vkBuffers, List<VkbImage> images) {
		this.usedVma = usedVma;
		this.vkAllocations = vkAllocations;
		this.vkBuffers = vkBuffers;
		this.images = images;
	}

	/**
	 * Destroys all buffers and images of this shared allocation, and frees all its memory allocations.
	 */
	public void free(BoilerInstance instance) {
		for (long buffer : vkBuffers) vkDestroyBuffer(instance.vkDevice(), buffer, null);
		for (VkbImage image : images) image.destroy(instance);
		if (usedVma) {
			for (long allocation : vkAllocations) vmaFreeMemory(instance.vmaAllocator(), allocation);
		} else {
			for (long allocation : vkAllocations) vkFreeMemory(instance.vkDevice(), allocation, null);
		}
	}
}
