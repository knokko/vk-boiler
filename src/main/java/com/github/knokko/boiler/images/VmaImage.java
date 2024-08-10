package com.github.knokko.boiler.images;

import com.github.knokko.boiler.instance.BoilerInstance;

import static org.lwjgl.util.vma.Vma.vmaDestroyImage;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.vkDestroyImageView;

public record VmaImage(
        long vkImage,
        long vkImageView,
        long vmaAllocation,
        int width, int height
) {
	public void destroy(BoilerInstance boiler) {
		if (vkImageView != VK_NULL_HANDLE) vkDestroyImageView(boiler.vkDevice(), vkImageView, null);
		vmaDestroyImage(boiler.vmaAllocator(), vkImage, vmaAllocation);
	}
}
