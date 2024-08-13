package com.github.knokko.boiler.window;

class VkbSwapchainImage {

    final long vkImage;
    final int index;

    VkbSwapchainImage(long vkImage, int index) {
        this.vkImage = vkImage;
        this.index = index;
    }
}
