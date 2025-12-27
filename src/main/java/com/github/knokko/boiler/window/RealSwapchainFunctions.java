package com.github.knokko.boiler.window;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.images.VkbImage;
import com.github.knokko.boiler.memory.callbacks.CallbackUserData;
import com.github.knokko.boiler.queues.VkbQueueFamily;
import com.github.knokko.boiler.synchronization.VkbFence;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.util.Objects;
import java.util.Set;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static java.lang.Math.max;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.EXTFragmentDensityMap.VK_IMAGE_USAGE_FRAGMENT_DENSITY_MAP_BIT_EXT;
import static org.lwjgl.vulkan.KHRFragmentShadingRate.VK_IMAGE_USAGE_FRAGMENT_SHADING_RATE_ATTACHMENT_BIT_KHR;
import static org.lwjgl.vulkan.KHRGetSurfaceCapabilities2.vkGetPhysicalDeviceSurfaceCapabilities2KHR;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.KHRVideoDecodeQueue.VK_IMAGE_USAGE_VIDEO_DECODE_DPB_BIT_KHR;
import static org.lwjgl.vulkan.KHRVideoDecodeQueue.VK_IMAGE_USAGE_VIDEO_DECODE_DST_BIT_KHR;
import static org.lwjgl.vulkan.KHRVideoEncodeQueue.VK_IMAGE_USAGE_VIDEO_ENCODE_DPB_BIT_KHR;
import static org.lwjgl.vulkan.KHRVideoEncodeQueue.VK_IMAGE_USAGE_VIDEO_ENCODE_SRC_BIT_KHR;
import static org.lwjgl.vulkan.QCOMImageProcessing.VK_IMAGE_USAGE_SAMPLE_BLOCK_MATCH_BIT_QCOM;
import static org.lwjgl.vulkan.QCOMImageProcessing.VK_IMAGE_USAGE_SAMPLE_WEIGHT_BIT_QCOM;
import static org.lwjgl.vulkan.VK10.*;

class RealSwapchainFunctions implements SwapchainFunctions {

	private final BoilerInstance instance;
	private final VkbQueueFamily presentFamily;
	private final WindowProperties properties;

	RealSwapchainFunctions(BoilerInstance instance, VkbQueueFamily presentFamily, WindowProperties properties) {
		this.instance = instance;
		this.presentFamily = presentFamily;
		this.properties = properties;
	}

	@Override
	public void deviceWaitIdle() {
		instance.deviceWaitIdle("RealSwapchainFunctions");
	}

	@Override
	public void getSurfaceCapabilities(VkSurfaceCapabilitiesKHR capabilities) {
		assertVkSuccess(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(
				instance.vkPhysicalDevice(), properties.vkSurface(), capabilities
		), "GetPhysicalDeviceSurfaceCapabilitiesKHR", "RealSwapchainFunctions");
	}

	@Override
	public SwapchainWrapper createSwapchain(
			PresentModes presentModes, int presentMode, Set<SwapchainResourceManager<?, ?>> associations,
			int width, int height, AcquireSemaphores acquireSemaphores, long oldSwapchain,
			VkSurfaceCapabilitiesKHR surfaceCapabilities, String debugName
	) {
		try (var stack = stackPush()) {
			// MAILBOX does triple-buffering, so we probably need an extra image to reach that potential throughput.
			int desiredImageCount = presentModes.current == VK_PRESENT_MODE_MAILBOX_KHR ? 3 : 2;

			/*
			 * Note the following clause in the Vulkan specification:
			 *
			 * Let S be the number of images in swapchain.
			 * If swapchain is created with VkSwapchainPresentModesCreateInfoKHR, let M be the maximum of the values in
			 * VkSurfaceCapabilitiesKHR::minImageCount when queried with each present mode in
			 * VkSwapchainPresentModesCreateInfoKHR::pPresentModes in VkSurfacePresentModeKHR.
			 * Otherwise, let M be the value of VkSurfaceCapabilitiesKHR::minImageCount without a
			 * VkSurfacePresentModeKHR as part of the query input.
			 *
			 * vkAcquireNextImageKHR should not be called if the number of images that the application has currently
			 * acquired is greater than S-M. If vkAcquireNextImageKHR is called when the number of images that the
			 * application has currently acquired is less than or equal to S-M, vkAcquireNextImageKHR must return in
			 * finite time with an allowed VkResult code.
			 *
			 *
			 * Luckily for us, we only acquire 1 swapchain image at the same time, so we should only request at least
			 * `1 + minImageCount` swapchain images.
			 */
			desiredImageCount = max(surfaceCapabilities.minImageCount() + 1, desiredImageCount);

			// Furthermore, we should ensure that we have minImageCount >= framesInFlight + 1,
			// since we can't reach 3 frames in flight with less than 3 swapchain images.
			// We also want the +1 as margin.
			desiredImageCount = max(properties.maxFramesInFlight() + 1, desiredImageCount);

			// Make sure we don't exceed maxImageCount
			if (surfaceCapabilities.maxImageCount() > 0 && desiredImageCount > surfaceCapabilities.maxImageCount()) {
				desiredImageCount = surfaceCapabilities.maxImageCount();
				System.err.println("Warning: " + desiredImageCount + " swapchain images are desired, " +
						"but only " + surfaceCapabilities.maxImageCount() + " are supported");
			}
			int minImageCount = max(desiredImageCount, surfaceCapabilities.minImageCount());

			presentModes.compatible.clear();
			presentModes.compatible.add(presentMode);

			var ciSwapchain = VkSwapchainCreateInfoKHR.calloc(stack);
			IntBuffer compatiblePresentModeBuffer = null;
			if (instance.extra.swapchainMaintenance()) {
				if (presentModes.used.size() > 1) {
					var presentModeCompatibility = VkSurfacePresentModeCompatibilityEXT.calloc(stack);
					presentModeCompatibility.sType$Default();

					var queriedPresentMode = VkSurfacePresentModeEXT.calloc(stack);
					queriedPresentMode.sType$Default();
					queriedPresentMode.presentMode(presentMode);

					var surfaceInfo = VkPhysicalDeviceSurfaceInfo2KHR.calloc(stack);
					surfaceInfo.sType$Default();
					surfaceInfo.pNext(queriedPresentMode);
					surfaceInfo.surface(properties.vkSurface());

					var surfaceCapabilities2 = VkSurfaceCapabilities2KHR.calloc(stack);
					surfaceCapabilities2.sType$Default();
					surfaceCapabilities2.pNext(presentModeCompatibility);

					assertVkSuccess(vkGetPhysicalDeviceSurfaceCapabilities2KHR(
							instance.vkPhysicalDevice(), surfaceInfo, surfaceCapabilities2
					), "GetPhysicalDeviceSurfaceCapabilities2KHR", "Present mode compatibility: count");

					int numCompatiblePresentModes = presentModeCompatibility.presentModeCount();

					compatiblePresentModeBuffer = stack.callocInt(numCompatiblePresentModes);
					presentModeCompatibility.pPresentModes(compatiblePresentModeBuffer);
					assertVkSuccess(vkGetPhysicalDeviceSurfaceCapabilities2KHR(
							instance.vkPhysicalDevice(), surfaceInfo, surfaceCapabilities2
					), "GetPhysicalDeviceSurfaceCapabilities2KHR", "Present mode compatibility: modes");
					ciSwapchain.minImageCount(max(desiredImageCount, surfaceCapabilities2.surfaceCapabilities().minImageCount()));

					int newWidth = surfaceCapabilities2.surfaceCapabilities().currentExtent().width();
					int newHeight = surfaceCapabilities2.surfaceCapabilities().currentExtent().height();
					if (newWidth != -1) width = newWidth;
					if (newHeight != -1) height = newHeight;

					minImageCount = max(minImageCount, surfaceCapabilities2.surfaceCapabilities().minImageCount());
				}

				IntBuffer pPresentModes = presentModes.createSwapchain(stack, presentMode, compatiblePresentModeBuffer);

				var ciPresentModes = VkSwapchainPresentModesCreateInfoEXT.calloc(stack);
				ciPresentModes.sType$Default();
				ciPresentModes.pPresentModes(pPresentModes);

				ciSwapchain.pNext(ciPresentModes);
			} else {
				presentModes.createSwapchain(null, presentMode, null);
			}

			ciSwapchain.sType$Default();
			ciSwapchain.flags(0);
			ciSwapchain.surface(properties.vkSurface());
			ciSwapchain.minImageCount(minImageCount);
			ciSwapchain.imageFormat(properties.surfaceFormat());
			ciSwapchain.imageColorSpace(properties.surfaceColorSpace());
			ciSwapchain.imageExtent().set(width, height);
			ciSwapchain.imageArrayLayers(1);
			ciSwapchain.imageUsage(properties.swapchainImageUsage());

			if (instance.queueFamilies().graphics() == presentFamily) {
				ciSwapchain.imageSharingMode(VK_SHARING_MODE_EXCLUSIVE);
			} else {
				ciSwapchain.imageSharingMode(VK_SHARING_MODE_CONCURRENT);
				ciSwapchain.queueFamilyIndexCount(2);
				ciSwapchain.pQueueFamilyIndices(stack.ints(
						instance.queueFamilies().graphics().index(), presentFamily.index()
				));
			}

			ciSwapchain.preTransform(surfaceCapabilities.currentTransform());
			ciSwapchain.compositeAlpha(properties.swapchainCompositeAlpha());
			ciSwapchain.presentMode(presentModes.current);
			ciSwapchain.clipped(true);
			ciSwapchain.oldSwapchain(oldSwapchain);

			var pSwapchain = stack.callocLong(1);
			assertVkSuccess(vkCreateSwapchainKHR(
					instance.vkDevice(), ciSwapchain, CallbackUserData.SWAPCHAIN.put(stack, instance), pSwapchain
			), "CreateSwapchainKHR", "RealSwapchainFunctions");
			long vkSwapchain = pSwapchain.get(0);

			instance.debug.name(stack, vkSwapchain, VK_OBJECT_TYPE_SWAPCHAIN_KHR, debugName);

			return new SwapchainWrapper(
					vkSwapchain, this, properties, presentModes, associations,
					width, height, acquireSemaphores, debugName
			);
		}
	}

	@Override
	public VkbImage[] getSwapchainImages(long vkSwapchain, int width, int height, int imageUsage, String debugName) {
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

			VkbImage[] result = new VkbImage[numImages];
			for (int index = 0; index < numImages; index++) {
				long vkImage = pImages.get(index);
				this.instance.debug.name(stack, vkImage, VK_OBJECT_TYPE_IMAGE, debugName + "Image" + index);

				result[index] = new VkbImage(vkImage, width, height, VK_IMAGE_ASPECT_COLOR_BIT);
				if (shouldCreateImageView(imageUsage)) {
					result[index].vkImageView = instance.images.createSimpleView(
							vkImage, properties.surfaceFormat(), VK_IMAGE_ASPECT_COLOR_BIT,
							debugName + "ImageView" + index
					);
				}
			}
			return result;
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

	@Override
	public int acquireImage(
			long vkSwapchain, MemoryStack stack, IntBuffer pImageIndex,
			VkbFence acquireFence, long acquireSemaphore
	) {
		long vkFence = acquireFence != null ? acquireFence.getVkFenceAndSubmit() : VK_NULL_HANDLE;
		return vkAcquireNextImageKHR(
				instance.vkDevice(), vkSwapchain, properties.acquireTimeout(),
				acquireSemaphore, vkFence, pImageIndex
		);
	}

	@Override
	public int presentImage(AcquiredImage image, boolean switchPresentMode) {
		try (var stack = stackPush()) {
			var presentInfo = VkPresentInfoKHR.calloc(stack);
			presentInfo.sType$Default();
			presentInfo.pWaitSemaphores(stack.longs(image.presentSemaphore));
			presentInfo.swapchainCount(1);
			presentInfo.pSwapchains(stack.longs(image.swapchain.vkSwapchain));
			presentInfo.pImageIndices(stack.ints(image.index));
			presentInfo.pResults(stack.callocInt(1));

			if (image.beforePresentCallback != null) image.beforePresentCallback.accept(presentInfo);

			if (switchPresentMode) {
				var changePresentMode = VkSwapchainPresentModeInfoEXT.calloc(stack);
				changePresentMode.sType$Default();
				changePresentMode.pPresentModes(stack.ints(image.presentMode));

				presentInfo.pNext(changePresentMode);
			}

			String debugName = image.swapchain.debugName + "Present" + image.index;
			int presentResult = presentFamily.first().present(presentInfo);
			assertVkSuccess(presentResult, "QueuePresentKHR", debugName, VK_ERROR_OUT_OF_DATE_KHR, VK_SUBOPTIMAL_KHR);
			assertVkSuccess(
					Objects.requireNonNull(presentInfo.pResults()).get(0),
					"QueuePresentKHR", debugName, VK_ERROR_OUT_OF_DATE_KHR, VK_SUBOPTIMAL_KHR
			);
			return presentResult;
		}
	}

	@Override
	public boolean hasSwapchainMaintenance() {
		return instance.extra.swapchainMaintenance();
	}

	@Override
	public VkbFence borrowFence(boolean startSignaled, String debugName) {
		return instance.sync.fenceBank.borrowFence(startSignaled, debugName);
	}

	@Override
	public void returnFence(VkbFence fence) {
		instance.sync.fenceBank.returnFence(fence);
	}

	@Override
	public long borrowSemaphore(String debugName) {
		return instance.sync.semaphoreBank.borrowSemaphore(debugName);
	}

	@Override
	public void returnSemaphore(long vkSemaphore) {
		instance.sync.semaphoreBank.returnSemaphores(vkSemaphore);
	}

	@Override
	public void destroySwapchain(long vkSwapchain, VkbImage[] images) {
		try (var stack = stackPush()) {
			vkDestroySwapchainKHR(instance.vkDevice(), vkSwapchain, CallbackUserData.SWAPCHAIN.put(stack, instance));
			for (VkbImage image : images) {
				vkDestroyImageView(
						instance.vkDevice(), image.vkImageView,
						CallbackUserData.IMAGE_VIEW.put(stack, instance)
				);
			}
		}
	}
}
