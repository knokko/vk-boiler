package com.github.knokko.boiler.swapchain;

import java.util.function.Consumer;

public record AcquireResult(
        long vkSwapchain,
        long vkImage,
        int imageIndex,
        int numSwapchainImages,
        long acquireSemaphore,
        long presentSemaphore,
        long presentFence,
        int width,
        int height,
        Object swapchain,
        long swapchainID,
        Consumer<Runnable> addPreDestructionCallback
) {
}
