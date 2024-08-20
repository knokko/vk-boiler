package com.github.knokko.boiler.window;

import com.github.knokko.boiler.sync.AwaitableSubmission;
import com.github.knokko.boiler.sync.VkbFence;
import org.lwjgl.vulkan.VkPresentInfoKHR;

import java.util.function.Consumer;

import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;

public class AcquiredImage {

	final VkbSwapchain swapchain;
	private final int index;
	final VkbFence acquireFence;
	final long acquireSemaphore;
	private final long presentSemaphore;
	final VkbFence presentFence;
	AwaitableSubmission renderSubmission;
	final int presentMode;
	public Consumer<VkPresentInfoKHR> beforePresentCallback;

	AcquiredImage(
			VkbSwapchain swapchain, int index, VkbFence acquireFence, long acquireSemaphore,
			long presentSemaphore, VkbFence presentFence, int presentMode
	) {
		this.swapchain = swapchain;
		this.index = index;
		this.acquireFence = acquireFence;
		this.acquireSemaphore = acquireSemaphore;
		this.presentSemaphore = presentSemaphore;
		this.presentFence = presentFence;
		this.presentMode = presentMode;
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
