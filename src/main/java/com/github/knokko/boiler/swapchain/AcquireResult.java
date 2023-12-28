package com.github.knokko.boiler.swapchain;

import com.github.knokko.boiler.sync.FatFence;

import java.util.function.Consumer;

public record AcquireResult(
        long vkSwapchain,
        long vkImage,
        int imageIndex,
        int numSwapchainImages,
        long acquireSemaphore,
        long presentSemaphore,
        FatFence presentFence,
        int width,
        int height,
        Object swapchain,
        long swapchainID,
        Consumer<Runnable> addPreDestructionCallback
) {
}
