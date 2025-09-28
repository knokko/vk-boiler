package com.github.knokko.boiler.window;

import org.lwjgl.vulkan.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.vulkan.VK10.*;

class SwapchainManager {

	private final SwapchainFunctions functions;
	private final WindowProperties properties;
	private final PresentModes presentModes;

	private final long[] acquireSemaphores;
	private final VkSurfaceCapabilitiesKHR surfaceCapabilities = VkSurfaceCapabilitiesKHR.calloc();

	private final Set<SwapchainResourceManager<?, ?>> associations = ConcurrentHashMap.newKeySet();
	private final List<SwapchainWrapper> oldSwapchains = new ArrayList<>();


	private SwapchainWrapper currentSwapchain;
	private int currentSwapchainID;
	int currentWidth, currentHeight;

	SwapchainManager(SwapchainFunctions functions, WindowProperties properties, PresentModes presentModes) {
		this.functions = functions;
		this.properties = properties;
		this.presentModes = presentModes;

		this.acquireSemaphores = new long[properties.maxFramesInFlight()];
		for (int frame = 0; frame < properties.maxFramesInFlight(); frame++) {
			acquireSemaphores[frame] = functions.borrowSemaphore(properties.title() + "Acquire" + frame);
		}

		updateSize();
	}

	private void maybeRecreateSwapchain(int presentMode) {
		int oldWidth = currentWidth;
		int oldHeight = currentHeight;

		updateSize();

		boolean shouldRecreate = oldWidth != currentWidth || oldHeight != currentHeight;
		if (currentWidth == 0 || currentHeight == 0) shouldRecreate = false;

		if (shouldRecreate) recreateSwapchain(presentMode);
	}

	AcquiredImage2 acquire(int presentMode, boolean useFence) {
//		instance.checkForFatalValidationErrors();
//		if (windowLoop != null && windowLoop.shouldCheckResize(this)) {
//			windowLoop.queueMainThreadAction(() -> maybeRecreateSwapchain(presentMode), this);
//		}

		presentModes.acquire(presentMode);

		if (currentSwapchain == null) recreateSwapchain(presentMode);
		if (currentSwapchain == null) return null;
		if (currentSwapchain.isOutdated()) recreateSwapchain(presentMode);
		if (currentSwapchain == null) return null;

		var acquiredImage = currentSwapchain.acquireImage(presentMode, currentWidth, currentHeight, useFence);
		if (acquiredImage == null) {
			recreateSwapchain(presentMode);
			if (currentSwapchain == null) return null;
			acquiredImage = currentSwapchain.acquireImage(presentMode, currentWidth, currentHeight, useFence);
		}

		return acquiredImage;
	}

	private void recreateSwapchain(int presentMode) {
		if (currentSwapchain != null) oldSwapchains.add(currentSwapchain);
		if (properties.maxOldSwapchains() > oldSwapchains.size()) {
			functions.deviceWaitIdle();
			for (var swapchain : oldSwapchains) swapchain.destroy();
			oldSwapchains.clear();
		}

		createSwapchain(presentMode, true);
	}

	private void createSwapchain(int presentMode, boolean updateSize) {
		if (updateSize) updateSize();
		if (currentWidth == 0 || currentHeight == 0) {
			currentSwapchain = null;
			return;
		}

		long oldSwapchain = VK_NULL_HANDLE;
		if (!oldSwapchains.isEmpty()) oldSwapchain = oldSwapchains.get(oldSwapchains.size() - 1).vkSwapchain;
		currentSwapchainID += 1;

		presentModes.createSwapchain(presentMode);
		currentSwapchain = functions.createSwapchain(
				presentModes, associations, currentWidth, currentHeight, acquireSemaphores,
				oldSwapchain, surfaceCapabilities, "Swapchain-" + properties.title() + currentSwapchainID
		);
	}

	void updateSize() {
		//assertMainThread();
		functions.getSurfaceCapabilities(surfaceCapabilities);
		int width = surfaceCapabilities.currentExtent().width();
		int height = surfaceCapabilities.currentExtent().height();

		if (width != -1) currentWidth = width;
		if (height != -1) currentHeight = height;

//			if (width == -1 || height == -1) {
//				var pWidth = stack.callocInt(1);
//				var pHeight = stack.callocInt(1);
//				if (instance.useSDL) {
//					assertSdlSuccess(SDL_GetWindowSizeInPixels(handle, pWidth, pHeight), "GetWindowSizeInPixels");
//				} else {
//					glfwGetFramebufferSize(handle, pWidth, pHeight);
//				}
//				width = pWidth.get(0);
//				height = pHeight.get(0);
//			}
//
//			this.width = width;
//			this.height = height;
	}

	/**
	 * Presents a previously acquired swapchain image
	 * @param image The swapchain image
	 */
	public void presentSwapchainImage(AcquiredImage2 image) {
		image.swapchain.presentImage(image);
//		if (hideUntilFirstFrame && !calledShowWindow) {
//			if (windowLoop == null) showWindowNow();
//			else windowLoop.queueMainThreadAction(this::showWindowNow, this);
//			calledShowWindow = true;
//		}
	}

	void destroy() {
		for (var association : associations) association.destroy();
		surfaceCapabilities.free();
		if (currentSwapchain != null || !oldSwapchains.isEmpty()) functions.deviceWaitIdle();
		for (SwapchainWrapper swapchain : oldSwapchains) swapchain.destroy();
		oldSwapchains.clear();
		if (currentSwapchain != null) currentSwapchain.destroy();
		for (long semaphore : acquireSemaphores) functions.returnSemaphore(semaphore);
	}
}
