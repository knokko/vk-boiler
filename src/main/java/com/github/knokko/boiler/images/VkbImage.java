package com.github.knokko.boiler.images;

import com.github.knokko.boiler.BoilerInstance;

import static org.lwjgl.util.vma.Vma.vmaDestroyImage;
import static org.lwjgl.vulkan.VK10.*;

/**
 * Wraps a <i>VkImage</i> and its size. Also stores an optional <i>VmaAllocation</i> and <i>VkImageView</i> + aspect
 * mask
 * @param vkImage The <i>VkImage</i> handle
 * @param vkImageView The optional <i>VkImageView</i> handle
 * @param vmaAllocation The optional <i>VmaAllocation</i> handle
 * @param width The width of the image, in pixels
 * @param height The height of the image, in pixels
 * @param aspectMask The optional aspect mask of the image view
 */
public record VkbImage(
		long vkImage,
		long vkImageView,
		long vmaAllocation,
		int width, int height,
		int aspectMask
) {

	/**
	 * Destroys the <i>VkImage</i> and the optional <i>VmaAllocation</i> and <i>VkImageView</i>
	 */
	public void destroy(BoilerInstance instance) {
		if (vkImageView != VK_NULL_HANDLE) vkDestroyImageView(instance.vkDevice(), vkImageView, null);
		if (vmaAllocation != VK_NULL_HANDLE) vmaDestroyImage(instance.vmaAllocator(), vkImage, vmaAllocation);
		else vkDestroyImage(instance.vkDevice(), vkImage, null);
	}
}
