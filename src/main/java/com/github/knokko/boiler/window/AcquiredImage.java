package com.github.knokko.boiler.window;

import com.github.knokko.boiler.images.VkbImage;
import com.github.knokko.boiler.synchronization.FenceSubmission;
import com.github.knokko.boiler.synchronization.VkbFence;
import org.lwjgl.vulkan.VkPresentInfoKHR;

import java.util.function.Consumer;

import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;

public class AcquiredImage {

	final SwapchainWrapper swapchain;
	final int index;
	final VkbImage image;
	final int presentMode;

	final long acquireSemaphore;
	final long presentSemaphore;
	final FenceSubmission acquireSubmission;
	final VkbFence presentFence;

	Consumer<VkPresentInfoKHR> beforePresentCallback;

	AcquiredImage(
			SwapchainWrapper swapchain, int index, VkbImage image, int presentMode,
			long acquireSemaphore, FenceSubmission acquireSubmission,
			long presentSemaphore, VkbFence presentFence
	) {
		this.swapchain = swapchain;
		this.index = index;
		this.image = image;
		this.presentMode = presentMode;
		this.acquireSemaphore = acquireSemaphore;
		this.acquireSubmission = acquireSubmission;
		this.presentSemaphore = presentSemaphore;
		this.presentFence = presentFence;
	}

	public int getIndex() {
		return index;
	}

	public VkbImage getImage() {
		return image;
	}

	public int getWidth() {
		return image.width;
	}

	public int getHeight() {
		return image.height;
	}

	/**
	 * If you acquired this swapchain image using <i>acquireSwapchainImageWithFence</i>, you must wait on this
	 * submission before submitting any commands that use this swapchain image. If not, you must not use this method.
	 */
	public FenceSubmission getAcquireSubmission() {
		if (acquireSemaphore != VK_NULL_HANDLE) throw new UnsupportedOperationException("You asked for a semaphore");
		return acquireSubmission;
	}

	/**
	 * If you acquired this swapchain image using <i>acquireSwapchainImageWithSemaphore</i>, you must add this
	 * semaphore to the wait semaphores of the first queue submission that uses this swapchain image. If not, you
	 * must not use this method.
	 */
	public long getAcquireSemaphore() {
		if (acquireSemaphore == VK_NULL_HANDLE) throw new UnsupportedOperationException("You asked for a fence");
		return acquireSemaphore;
	}

	/**
	 * When you do the last submission that uses the swapchain image, you must add this semaphore to the signal
	 * semaphores of that submission.
	 */
	public long getPresentSemaphore() {
		return presentSemaphore;
	}
}
