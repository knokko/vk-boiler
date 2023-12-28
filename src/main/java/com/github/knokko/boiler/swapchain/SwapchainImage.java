package com.github.knokko.boiler.swapchain;

import com.github.knokko.boiler.sync.FatFence;
import com.github.knokko.boiler.sync.TimelineInstant;

class SwapchainImage {

    final long vkImage;
    final int index;

    long acquireSemaphore, presentSemaphore;
    FatFence acquireFence, presentFence, drawingFence;
    TimelineInstant drawingSemaphore;

    SwapchainImage(long vkImage, int index) {
        this.vkImage = vkImage;
        this.index = index;
    }
}
