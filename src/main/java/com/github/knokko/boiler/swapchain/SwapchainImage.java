package com.github.knokko.boiler.swapchain;

import com.github.knokko.boiler.sync.FatFence;

import java.util.function.BooleanSupplier;

class SwapchainImage {

    final long vkImage;
    final int index;

    long acquireSemaphore, presentSemaphore;
    FatFence acquireFence, presentFence;
    BooleanSupplier didDrawingFinish;

    SwapchainImage(long vkImage, int index) {
        this.vkImage = vkImage;
        this.index = index;
    }
}
