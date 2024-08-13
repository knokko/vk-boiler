package com.github.knokko.boiler.window;

import com.github.knokko.boiler.sync.VkbFence;

public class AcquiredImage {

    final VkbSwapchain swapchain;
    private final VkbSwapchainImage image;
    private final VkbFence acquireFence;
    private final long acquireSemaphore;
    private final long presentSemaphore;
    final VkbFence presentFence;

    AcquiredImage(
            VkbSwapchain swapchain, VkbSwapchainImage image,
            VkbFence acquireFence, long presentSemaphore, VkbFence presentFence
    ) {
        this.swapchain = swapchain;
        this.image = image;
        this.acquireFence = acquireFence;
        this.acquireSemaphore = 0;
        this.presentSemaphore = presentSemaphore;
        this.presentFence = presentFence;
    }

    AcquiredImage(
            VkbSwapchain swapchain, VkbSwapchainImage image,
            long acquireSemaphore, long presentSemaphore, VkbFence presentFence
    ) {
        this.swapchain = swapchain;
        this.image = image;
        this.acquireFence = null;
        this.acquireSemaphore = acquireSemaphore;
        this.presentSemaphore = presentSemaphore;
        this.presentFence = presentFence;
    }

    public int index() {
        return image.index;
    }

    public long vkImage() {
        return image.vkImage;
    }

    public int width() {
        return swapchain.width;
    }

    public int height() {
        return swapchain.height;
    }

    public VkbFence acquireFence() {
        if (acquireFence == null) throw new UnsupportedOperationException("You asked for a semaphore");
        return acquireFence;
    }

    public long acquireSemaphore() {
        if (acquireSemaphore == 0) throw new UnsupportedOperationException("You asked for a fence");
        return acquireSemaphore;
    }

    public long presentSemaphore() {
        return presentSemaphore;
    }
}
