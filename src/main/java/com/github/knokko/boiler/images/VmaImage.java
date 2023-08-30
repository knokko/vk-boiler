package com.github.knokko.boiler.images;

public record VmaImage(
        long vkImage,
        long vkImageView,
        long vmaAllocation,
        int width, int height
) {
}
