package com.github.knokko.boiler.window;

import com.github.knokko.boiler.synchronization.AwaitableSubmission;
import com.github.knokko.boiler.synchronization.VkbFence;
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

	/**
	 * This callback will be called right before the swapchain manager class <i>vkQueuePresentKHR</i>. You can use this
	 * to e.g. chain structures to the <i>pNext</i> chain of the <i>VkPresentInfoKHR</i>.<br>
	 *
	 * When you acquire an image, this field will initially be <i>null</i>. You can simply change the value of this
	 * field to your callback.
	 */
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

	/**
	 * @return The swapchain image index
	 */
	public int index() {
		return index;
	}

	/**
	 * @return The swapchain image handle
	 */
	public long vkImage() {
		return swapchain.images[index];
	}

	/**
	 * @return The width of the swapchain (image), in pixels
	 */
	public int width() {
		return swapchain.width;
	}

	/**
	 * @return The height of the swapchain (image), in pixels
	 */
	public int height() {
		return swapchain.height;
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

	/**
	 * When you do the last submission that uses the swapchain image, you must add this semaphore to the signal
	 * semaphores of that submission.
	 */
	public long presentSemaphore() {
		return presentSemaphore;
	}
}
