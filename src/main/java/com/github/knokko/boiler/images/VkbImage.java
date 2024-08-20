package com.github.knokko.boiler.images;

import com.github.knokko.boiler.BoilerInstance;

import static org.lwjgl.util.vma.Vma.vmaDestroyImage;
import static org.lwjgl.vulkan.VK10.*;

public record VkbImage(
		long vkImage,
		long vkImageView,
		long vmaAllocation,
		int width, int height
) {
	public void destroy(BoilerInstance instance) {
		if (vkImageView != VK_NULL_HANDLE) vkDestroyImageView(instance.vkDevice(), vkImageView, null);
		if (vmaAllocation != VK_NULL_HANDLE) vmaDestroyImage(instance.vmaAllocator(), vkImage, vmaAllocation);
		else vkDestroyImage(instance.vkDevice(), vkImage, null); // TODO Test this
	}
}
