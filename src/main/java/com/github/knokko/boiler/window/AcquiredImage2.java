package com.github.knokko.boiler.window;

import com.github.knokko.boiler.images.VkbImage;
import com.github.knokko.boiler.synchronization.VkbFence;
import org.lwjgl.vulkan.VkPresentInfoKHR;

import java.util.function.Consumer;

import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;

public class AcquiredImage2 {

	final SwapchainWrapper swapchain;
	public final int index;
	public final VkbImage image;
	final int presentMode;

	private final long acquireSemaphore;

	/**
	 * When you do the last submission that uses the swapchain image, you must add this semaphore to the signal
	 * semaphores of that submission.
	 */
	public final long presentSemaphore;
	final VkbFence acquireFence, presentFence;

	Consumer<VkPresentInfoKHR> beforePresentCallback;

	AcquiredImage2(
			SwapchainWrapper swapchain, int index, VkbImage image, int presentMode,
			long acquireSemaphore, VkbFence acquireFence,
			long presentSemaphore, VkbFence presentFence
	) {
		this.swapchain = swapchain;
		this.index = index;
		this.image = image;
		this.presentMode = presentMode;
		this.acquireSemaphore = acquireSemaphore;
		this.acquireFence = acquireFence;
		this.presentSemaphore = presentSemaphore;
		this.presentFence = presentFence;
	}

	public int getWidth() {
		return image.width;
	}

	public int getHeight() {
		return image.height;
	}

	/**
	 * If you acquired this swapchain image using <i>acquireSwapchainImageWithFence</i>, you must wait on this fence
	 * before submitting any commands that use this swapchain image. If not, you must not use this method.
	 */
	public VkbFence acquireFence() {
		if (acquireSemaphore != VK_NULL_HANDLE) throw new UnsupportedOperationException("You asked for a semaphore");
		return acquireFence;
	}

	/**
	 * If you acquired this swapchain image using <i>acquireSwapchainImageWithSemaphore</i>, you must add this
	 * semaphore to the wait semaphores of the first queue submission that uses this swapchain image. If not, you
	 * must not use this method.
	 */
	public long acquireSemaphore() {
		if (acquireSemaphore == VK_NULL_HANDLE) throw new UnsupportedOperationException("You asked for a fence");
		return acquireSemaphore;
	}
}
