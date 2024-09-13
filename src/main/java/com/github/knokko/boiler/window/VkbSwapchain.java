package com.github.knokko.boiler.window;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.queues.VkbQueueFamily;
import com.github.knokko.boiler.synchronization.AwaitableSubmission;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkSwapchainPresentModeInfoEXT;

import java.util.*;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.EXTFragmentDensityMap.VK_IMAGE_USAGE_FRAGMENT_DENSITY_MAP_BIT_EXT;
import static org.lwjgl.vulkan.KHRFragmentShadingRate.VK_IMAGE_USAGE_FRAGMENT_SHADING_RATE_ATTACHMENT_BIT_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.KHRVideoDecodeQueue.VK_IMAGE_USAGE_VIDEO_DECODE_DPB_BIT_KHR;
import static org.lwjgl.vulkan.KHRVideoDecodeQueue.VK_IMAGE_USAGE_VIDEO_DECODE_DST_BIT_KHR;
import static org.lwjgl.vulkan.KHRVideoEncodeQueue.VK_IMAGE_USAGE_VIDEO_ENCODE_DPB_BIT_KHR;
import static org.lwjgl.vulkan.KHRVideoEncodeQueue.VK_IMAGE_USAGE_VIDEO_ENCODE_SRC_BIT_KHR;
import static org.lwjgl.vulkan.QCOMImageProcessing.VK_IMAGE_USAGE_SAMPLE_BLOCK_MATCH_BIT_QCOM;
import static org.lwjgl.vulkan.QCOMImageProcessing.VK_IMAGE_USAGE_SAMPLE_WEIGHT_BIT_QCOM;
import static org.lwjgl.vulkan.VK10.*;

class VkbSwapchain {

	private final BoilerInstance instance;
	final long vkSwapchain;
	private final SwapchainCleaner cleaner;
	private final Set<Integer> supportedPresentModes;
	private int presentMode;
	final int width, height;
	VkbQueueFamily presentFamily;
	final long[] images;
	final long[] imageViews;

	final Collection<Runnable> destructionCallbacks = new ArrayList<>();


	private boolean outdated;

	VkbSwapchain(
			BoilerInstance instance, long vkSwapchain, String title, SwapchainCleaner cleaner, int imageFormat, int imageUsage,
			int presentMode, int width, int height, VkbQueueFamily presentFamily, Set<Integer> supportedPresentModes
	) {
		this.instance = instance;
		this.vkSwapchain = vkSwapchain;
		this.cleaner = cleaner;

		this.presentMode = presentMode;
		this.supportedPresentModes = Collections.unmodifiableSet(supportedPresentModes);
		this.width = width;
		this.height = height;
		this.presentFamily = presentFamily;

		try (var stack = stackPush()) {
			var pNumImages = stack.callocInt(1);
			assertVkSuccess(vkGetSwapchainImagesKHR(
					instance.vkDevice(), vkSwapchain, pNumImages, null
			), "GetSwapchainImagesKHR", "count");
			int numImages = pNumImages.get(0);

			var pImages = stack.callocLong(numImages);
			assertVkSuccess(vkGetSwapchainImagesKHR(
					instance.vkDevice(), vkSwapchain, pNumImages, pImages
			), "GetSwapchainImagesKHR", "images");

			this.images = new long[numImages];
			this.imageViews = new long[numImages];
			for (int index = 0; index < numImages; index++) {
				this.images[index] = pImages.get(index);
				this.instance.debug.name(stack, this.images[index], VK_OBJECT_TYPE_IMAGE, "SwapchainImage-" + title + index);

				if (shouldCreateImageView(imageUsage)) {
					this.imageViews[index] = instance.images.createSimpleView(
							this.images[index], imageFormat, VK_IMAGE_ASPECT_COLOR_BIT, "SwapchainView-" + title + index
					);
				}
			}
		}
	}

	private static boolean shouldCreateImageView(int swapchainImageUsage) {
		// See https://vulkan.lunarg.com/doc/view/1.3.290.0/windows/1.3-extensions/vkspec.html#valid-imageview-imageusage
		int imageViewUsages = VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_STORAGE_BIT |
				VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT |
				VK_IMAGE_USAGE_INPUT_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSIENT_ATTACHMENT_BIT |
				VK_IMAGE_USAGE_FRAGMENT_SHADING_RATE_ATTACHMENT_BIT_KHR | VK_IMAGE_USAGE_FRAGMENT_DENSITY_MAP_BIT_EXT |
				VK_IMAGE_USAGE_VIDEO_DECODE_DST_BIT_KHR | VK_IMAGE_USAGE_VIDEO_DECODE_DPB_BIT_KHR |
				VK_IMAGE_USAGE_VIDEO_ENCODE_SRC_BIT_KHR | VK_IMAGE_USAGE_VIDEO_ENCODE_DPB_BIT_KHR |
				VK_IMAGE_USAGE_SAMPLE_WEIGHT_BIT_QCOM | VK_IMAGE_USAGE_SAMPLE_BLOCK_MATCH_BIT_QCOM;
		return (imageViewUsages & swapchainImageUsage) != 0;
	}

	public boolean isOutdated() {
		return outdated;
	}

	AcquiredImage acquireImage(int presentMode, int width, int height, boolean useAcquireFence) {
		if (!this.supportedPresentModes.contains(presentMode)) outdated = true;
		if (width != this.width || height != this.height) outdated = true;
		if (outdated) return null;

		try (var stack = stackPush()) {
			var acquireFence = instance.sync.fenceBank.borrowFence(false, "AcquireFence");
			long acquireSemaphore;

			if (useAcquireFence) {
				acquireSemaphore = VK_NULL_HANDLE;
			} else {
				acquireSemaphore = instance.sync.semaphoreBank.borrowSemaphore("AcquireSemaphore");
			}

			var pImageIndex = stack.callocInt(1);
			int acquireResult = vkAcquireNextImageKHR(
					instance.vkDevice(), vkSwapchain, instance.defaultTimeout,
					acquireSemaphore, acquireFence.getVkFenceAndSubmit(), pImageIndex
			);

			if (acquireResult == VK_SUBOPTIMAL_KHR || acquireResult == VK_ERROR_OUT_OF_DATE_KHR) {
				outdated = true;
			}

			if (acquireResult == VK_SUCCESS || acquireResult == VK_SUBOPTIMAL_KHR) {
				int imageIndex = pImageIndex.get(0);

				var presentSemaphore = instance.sync.semaphoreBank.borrowSemaphore("PresentSemaphore");
				var presentFence = cleaner.getPresentFence();
				AcquiredImage acquiredImage = new AcquiredImage(
						this, imageIndex, acquireFence, acquireSemaphore,
						presentSemaphore, presentFence, presentMode
				);

				cleaner.onAcquire(acquiredImage);

				return acquiredImage;
			}

			if (acquireResult == VK_ERROR_OUT_OF_DATE_KHR) return null;
			else {
				assertVkSuccess(acquireResult, "AcquireNextImageKHR", null);
				throw new Error("This code should be unreachable");
			}
		}
	}

	void presentImage(AcquiredImage image, AwaitableSubmission renderSubmission) {
		try (var stack = stackPush()) {
			var presentInfo = VkPresentInfoKHR.calloc(stack);
			presentInfo.sType$Default();
			presentInfo.pWaitSemaphores(stack.longs(image.presentSemaphore()));
			presentInfo.swapchainCount(1);
			presentInfo.pSwapchains(stack.longs(vkSwapchain));
			presentInfo.pImageIndices(stack.ints(image.index()));
			presentInfo.pResults(stack.callocInt(1));

			if (image.beforePresentCallback != null) image.beforePresentCallback.accept(presentInfo);

			if (image.presentMode != this.presentMode) {
				this.presentMode = image.presentMode;

				var changePresentMode = VkSwapchainPresentModeInfoEXT.calloc(stack);
				changePresentMode.sType$Default();
				changePresentMode.pPresentModes(stack.ints(image.presentMode));

				presentInfo.pNext(changePresentMode);
			}

			image.renderSubmission = renderSubmission;
			cleaner.beforePresent(stack, presentInfo, image);


			int presentResult = presentFamily.first().present(presentInfo);
			if (presentResult == VK_ERROR_OUT_OF_DATE_KHR || presentResult == VK_SUBOPTIMAL_KHR) {
				outdated = true;
				return;
			}
			assertVkSuccess(presentResult, "QueuePresentKHR", null);
			assertVkSuccess(Objects.requireNonNull(presentInfo.pResults()).get(0), "QueuePresentKHR", null);
		}
	}

	void destroyNow() {
		for (var callback : destructionCallbacks) callback.run();
		for (long imageView : imageViews) vkDestroyImageView(instance.vkDevice(), imageView, null);
		vkDestroySwapchainKHR(instance.vkDevice(), vkSwapchain, null);
	}
}
