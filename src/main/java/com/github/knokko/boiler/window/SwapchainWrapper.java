package com.github.knokko.boiler.window;

import com.github.knokko.boiler.exceptions.VulkanFailureException;
import com.github.knokko.boiler.images.VkbImage;
import com.github.knokko.boiler.synchronization.AwaitableSubmission;
import com.github.knokko.boiler.synchronization.FenceSubmission;
import com.github.knokko.boiler.synchronization.VkbFence;

import java.nio.IntBuffer;
import java.util.*;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;

class SwapchainWrapper {

	final long vkSwapchain;
	private final SwapchainFunctions functions;
	private final PresentModes presentModes;
	private final VkbImage[] swapchainImages;
	private final long[] presentSemaphores;
	private final AcquireSemaphores acquireSemaphores;
	final String debugName;

	final Collection<Runnable> destructionCallbacks = new ArrayList<>();
	final Set<SwapchainResourceManager<?, ?>> associations;
	private final int width, height;

	private AwaitableSubmission canDestroyOldSwapchains;
	private boolean outdated;

	SwapchainWrapper(
			long vkSwapchain, SwapchainFunctions functions, WindowProperties properties, PresentModes presentModes,
			Set<SwapchainResourceManager<?, ?>> associations, int width, int height,
			AcquireSemaphores acquireSemaphores, String debugName
	) {
		this.vkSwapchain = vkSwapchain;
		this.functions = functions;
		this.presentModes = presentModes;
		this.associations = associations;
		this.width = width;
		this.height = height;
		this.swapchainImages = functions.getSwapchainImages(
				vkSwapchain, width, height, properties.swapchainImageUsage(), debugName
		);
		this.presentSemaphores = new long[swapchainImages.length];
		this.acquireSemaphores = acquireSemaphores;
		this.debugName = debugName;
	}

	int getNumImages() {
		return swapchainImages.length;
	}

	long getPresentSemaphore(int imageIndex) {
		if (presentSemaphores[imageIndex] == VK_NULL_HANDLE) {
			presentSemaphores[imageIndex] = functions.borrowSemaphore(debugName + "Present" + imageIndex);
		}
		return presentSemaphores[imageIndex];
	}

	boolean isOutdated() {
		return outdated;
	}

	boolean canDestroyOldSwapchains() {
		return canDestroyOldSwapchains != null && canDestroyOldSwapchains.hasCompleted();
	}

	AcquiredImage2 acquireImage(int presentMode, int width, int height, boolean useFence) {
		if (width != this.width || height != this.height) outdated = true;
		if (outdated) return null;

		long acquireSemaphore = VK_NULL_HANDLE;
		VkbFence acquireFence = null;
		VkbFence presentFence = null;

		if (useFence || (canDestroyOldSwapchains == null && !functions.hasSwapchainMaintenance())) {
			acquireFence = functions.borrowFence(false, debugName + "Acquire");

			if (canDestroyOldSwapchains == null && !functions.hasSwapchainMaintenance()) {
				canDestroyOldSwapchains = new FenceSubmission(acquireFence);
			}
		}

		if (!useFence) acquireSemaphore = acquireSemaphores.next();

		int imageIndex;
		int acquireResult;
		try (var stack = stackPush()) {
			IntBuffer pImageIndex = stack.callocInt(1);
			acquireResult = functions.acquireImage(vkSwapchain, stack, pImageIndex, acquireFence, acquireSemaphore);
			imageIndex = pImageIndex.get(0);
		}

		if (acquireResult == VK_SUCCESS || acquireResult == VK_SUBOPTIMAL_KHR) {
			if (acquireResult == VK_SUBOPTIMAL_KHR) outdated = true;
			if (!useFence && acquireFence != null) functions.returnFence(acquireFence);

			if (canDestroyOldSwapchains == null && functions.hasSwapchainMaintenance()) {
				presentFence = functions.borrowFence(false, debugName + "Present");
				canDestroyOldSwapchains = new FenceSubmission(presentFence);
			}

			long presentSemaphore = getPresentSemaphore(imageIndex);
			return new AcquiredImage2(
					this, imageIndex, swapchainImages[imageIndex], presentMode,
					acquireSemaphore, acquireFence,
					presentSemaphore, presentFence
			);
		} else if (acquireResult == VK_ERROR_OUT_OF_DATE_KHR) {
			if (acquireFence != null) functions.returnFence(acquireFence);
			outdated = true;
			return null;
		} else {
			throw new VulkanFailureException("AcquireNextImageKHR", acquireResult, "SwapchainWrapper");
		}
	}

	void presentImage(AcquiredImage2 image) {
		int presentResult = functions.presentImage(image, presentModes.present(image.presentMode));
		if (presentResult == VK_ERROR_OUT_OF_DATE_KHR || presentResult == VK_SUBOPTIMAL_KHR) outdated = true;
		if (image.presentFence != null) functions.returnFence(image.presentFence);
	}

	void destroy() {
		for (var callback : destructionCallbacks) callback.run();
		functions.destroySwapchain(vkSwapchain, swapchainImages);
		for (long semaphore : presentSemaphores) {
			if (semaphore != VK_NULL_HANDLE) functions.returnSemaphore(semaphore);
		}
	}
}
