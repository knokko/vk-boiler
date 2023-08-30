package com.github.knokko.boiler.swapchain;

import java.util.function.Consumer;

public record AcquireResult(
        long vkSwapchain,
        long vkImage,
        int imageIndex,
        int numSwapchainImages,
        long acquireSemaphore,
        long presentSemaphore,
        int width,
        int height,
        long swapchainID,
        Consumer<Runnable> addPreDestructionCallback
) {
}
