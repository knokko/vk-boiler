package com.github.knokko.boiler.memory;

import com.github.knokko.boiler.BoilerInstance;

import java.util.ArrayList;
import java.util.Collection;

import static org.lwjgl.util.vma.Vma.vmaFreeMemory;
import static org.lwjgl.vulkan.VK10.*;

public class MemoryBlock {

	final Collection<Long> vkAllocations = new ArrayList<>();
	final Collection<Long> vmaAllocations = new ArrayList<>();
	final Collection<Long> vkBuffers = new ArrayList<>();
	final Collection<Long> vkImages = new ArrayList<>();
	final Collection<Long> vkImageViews = new ArrayList<>();

	public void free(BoilerInstance instance) {
		for (long vkImageView : vkImageViews) vkDestroyImageView(instance.vkDevice(), vkImageView, null);
		for (long vkImage : vkImages) vkDestroyImage(instance.vkDevice(), vkImage, null);
		for (long vkBuffer : vkBuffers) vkDestroyBuffer(instance.vkDevice(), vkBuffer, null);
		for (long vmaAllocation : vmaAllocations) vmaFreeMemory(instance.vmaAllocator(), vmaAllocation);
		for (long vkAllocation : vkAllocations) vkFreeMemory(instance.vkDevice(), vkAllocation, null);
	}
}
