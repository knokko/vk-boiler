package com.github.knokko.boiler.window;

import com.github.knokko.boiler.sync.AwaitableSubmission;
import com.github.knokko.boiler.sync.FenceSubmission;
import com.github.knokko.boiler.sync.VkbFence;

import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;

public class AcquiredImage {

    final VkbSwapchain swapchain;
    private final int index;
    final VkbFence acquireFence;
    final long acquireSemaphore;
    private final long presentSemaphore;
    final VkbFence presentFence;
    AwaitableSubmission renderSubmission;

    AcquiredImage(
            VkbSwapchain swapchain, int index,
            VkbFence acquireFence, long acquireSemaphore, long presentSemaphore, VkbFence presentFence
    ) {
        this.swapchain = swapchain;
        this.index = index;
        this.acquireFence = acquireFence;
        this.acquireSemaphore = acquireSemaphore;
        this.presentSemaphore = presentSemaphore;
        this.presentFence = presentFence;
    }

    public int index() {
        return index;
    }

    public long vkImage() {
        return swapchain.images[index];
    }

    public int width() {
        return swapchain.width;
    }

    public int height() {
        return swapchain.height;
    }

    public VkbFence acquireFence() {
        if (acquireSemaphore != VK_NULL_HANDLE) throw new UnsupportedOperationException("You asked for a semaphore");
        return acquireFence;
    }

    public long acquireSemaphore() {
        if (acquireSemaphore == VK_NULL_HANDLE) throw new UnsupportedOperationException("You asked for a fence");
        return acquireSemaphore;
    }

    public long presentSemaphore() {
        return presentSemaphore;
    }
}