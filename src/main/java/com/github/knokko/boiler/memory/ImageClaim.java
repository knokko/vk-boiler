package com.github.knokko.boiler.memory;

import com.github.knokko.boiler.images.ImageBuilder;
import com.github.knokko.boiler.images.VkbImage;

class ImageClaim {

	final VkbImage image;
	final ImageBuilder builder;
	final long memorySize;
	final long alignment;
	long memoryOffset = 0L;

	ImageClaim(VkbImage image, ImageBuilder builder, long memorySize, long alignment) {
		this.image = image;
		this.builder = builder;
		this.memorySize = memorySize;
		this.alignment = alignment;
	}
}
