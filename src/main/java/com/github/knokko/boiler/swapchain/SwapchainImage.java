package com.github.knokko.boiler.swapchain;

import com.github.knokko.boiler.sync.VkbFence;

import java.util.function.BooleanSupplier;

class SwapchainImage {

    final long vkImage;
    final int index;

    long acquireSemaphore, presentSemaphore;
    VkbFence acquireFence, presentFence;
    BooleanSupplier didDrawingFinish;

    SwapchainImage(long vkImage, int index) {
        this.vkImage = vkImage;
        this.index = index;
    }
}
