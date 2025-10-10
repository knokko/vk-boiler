package com.github.knokko.boiler.window;

import org.lwjgl.vulkan.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.vulkan.VK10.*;

class SwapchainManager {

	private final SwapchainFunctions functions;
	private final WindowProperties properties;
	private final PresentModes presentModes;
	private final AcquireSemaphores acquireSemaphores;
	private final SizeTracker sizeTracker;
	private final VkSurfaceCapabilitiesKHR surfaceCapabilities = VkSurfaceCapabilitiesKHR.calloc();

	private final Set<SwapchainResourceManager<?, ?>> associations = ConcurrentHashMap.newKeySet();
	private final List<SwapchainWrapper> oldSwapchains = new ArrayList<>();

	private SwapchainWrapper currentSwapchain;
	private int currentSwapchainID;

	SwapchainManager(SwapchainFunctions functions, WindowProperties properties, PresentModes presentModes) {
		this.functions = functions;
		this.properties = properties;
		this.presentModes = presentModes;
		this.acquireSemaphores = new AcquireSemaphores(
				// Take 1 semaphore margin to allow resetting the command buffer/pool AFTER acquiring
				functions, properties.title(), properties.maxFramesInFlight() + 1
		);
		this.sizeTracker = new SizeTracker(functions, surfaceCapabilities);
	}

	AcquiredImage acquire(int presentMode, boolean useFence) {
		sizeTracker.update();

		if (!presentModes.acquire(presentMode)) recreateSwapchain(presentMode);

		if (currentSwapchain == null) recreateSwapchain(presentMode);
		if (currentSwapchain == null) return null;
		currentSwapchain.updateWindowSize(sizeTracker.getWindowWidth(), sizeTracker.getWindowHeight());
		if (currentSwapchain.isOutdated()) recreateSwapchain(presentMode);
		if (currentSwapchain == null) return null;

		var acquiredImage = currentSwapchain.acquireImage(
				presentMode, useFence, !oldSwapchains.isEmpty()
		);
		if (acquiredImage == null) {
			recreateSwapchain(presentMode);
			if (currentSwapchain == null) return null;
			acquiredImage = currentSwapchain.acquireImage(
					presentMode, useFence, !oldSwapchains.isEmpty()
			);
		}

		if (currentSwapchain != null && !oldSwapchains.isEmpty() && currentSwapchain.canDestroyOldSwapchains()) {
			for (SwapchainWrapper old : oldSwapchains) old.destroy();
			oldSwapchains.clear();
		}

		return acquiredImage;
	}

	private void recreateSwapchain(int presentMode) {
		if (currentSwapchain != null) {
			oldSwapchains.add(currentSwapchain);
			currentSwapchain = null;
		}
		if (oldSwapchains.size() > properties.maxOldSwapchains()) {
			functions.deviceWaitIdle();
			for (var swapchain : oldSwapchains) swapchain.destroy();
			oldSwapchains.clear();
		}

		if (sizeTracker.getWindowWidth() == 0 || sizeTracker.getWindowHeight() == 0) return;

		long oldSwapchain = VK_NULL_HANDLE;
		if (!oldSwapchains.isEmpty()) oldSwapchain = oldSwapchains.get(oldSwapchains.size() - 1).vkSwapchain;
		currentSwapchainID += 1;

		currentSwapchain = functions.createSwapchain(
				presentModes, presentMode, associations,
				sizeTracker.getWindowWidth(), sizeTracker.getWindowHeight(),
				acquireSemaphores, oldSwapchain, surfaceCapabilities,
				"Swapchain-" + properties.title() + currentSwapchainID
		);
	}

	void setWindowSizeFromMainThread(int width, int height) {
		sizeTracker.setWindowSizeFromMainThread(width, height);
	}

	int getWidth() {
		return sizeTracker.getWindowWidth();
	}

	int getHeight() {
		return sizeTracker.getWindowHeight();
	}

	void destroy() {
		surfaceCapabilities.free();
		if (currentSwapchain != null || !oldSwapchains.isEmpty()) functions.deviceWaitIdle();
		for (SwapchainWrapper swapchain : oldSwapchains) swapchain.destroy();
		oldSwapchains.clear();
		if (currentSwapchain != null) currentSwapchain.destroy();
		acquireSemaphores.destroy();
		for (var association : associations) association.destroy();
	}
}
