package com.github.knokko.boiler.window;

import org.lwjgl.vulkan.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.vulkan.VK10.*;

class SwapchainManager {

	private final int maxOldSwapchains;
	private final List<SwapchainWrapper> oldSwapchains = new ArrayList<>();

	private final long[] acquireSemaphores;
	private final Set<Integer> supportedPresentModes = new HashSet<>();
	private final Set<Integer> usedPresentModes = new HashSet<>();
	private final Set<SwapchainResourceManager<?, ?>> associations = ConcurrentHashMap.newKeySet();

	private final SwapchainFunctions functions;
	private final String debugName;

	private SwapchainWrapper currentSwapchain;
	private int currentSwapchainID;
	int currentWidth, currentHeight;

	private final VkSurfaceCapabilitiesKHR surfaceCapabilities = VkSurfaceCapabilitiesKHR.calloc();

	SwapchainManager(
			int framesInFlight, int maxOldSwapchains, Collection<Integer> supportedPresentModes,
			Collection<Integer> preparedPresentModes, SwapchainFunctions functions, String debugName
	) {
		this.acquireSemaphores = new long[framesInFlight];
		for (int frame = 0; frame < framesInFlight; frame++) {
			acquireSemaphores[frame] = functions.borrowSemaphore(debugName + "Acquire" + frame);
		}
		this.maxOldSwapchains = maxOldSwapchains;
		this.usedPresentModes.addAll(preparedPresentModes);
		this.supportedPresentModes.addAll(supportedPresentModes);
		this.functions = functions;
		this.debugName = debugName;
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

		if (!supportedPresentModes.contains(presentMode)) {
			throw new IllegalArgumentException(
					"Unsupported present mode " + presentMode + ": supported present modes are " + supportedPresentModes
			);
		}
		this.usedPresentModes.add(presentMode);

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
		if (maxOldSwapchains > oldSwapchains.size()) {
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

		currentSwapchain = functions.createSwapchain(
				presentMode, usedPresentModes, associations, currentWidth, currentHeight, acquireSemaphores,
				oldSwapchain, surfaceCapabilities, "Swapchain-" + debugName + currentSwapchainID
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
