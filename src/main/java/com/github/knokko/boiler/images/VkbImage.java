package com.github.knokko.boiler.images;

/**
 * Wraps a <i>VkImage</i> and its size. Also stores an optional <i>VkImageView</i> + aspect mask
 */
public class VkbImage {

	/**
	 * The <b>VkImage</b> handle
	 */
	public final long vkImage;

	/**
	 * The optional <b>VkImageView</b> handle
	 */
	public long vkImageView;

	/**
	 * The size of the image, in pixels
	 */
	public final int width, height;

	/**
	 * The optional aspect mask of the image view
	 */
	public final int aspectMask;

	public VkbImage(long vkImage, int width, int height, int aspectMask) {
		this.vkImage = vkImage;
		this.width = width;
		this.height = height;
		this.aspectMask = aspectMask;
	}
}
