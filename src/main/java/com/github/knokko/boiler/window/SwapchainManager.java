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
	private final VkSurfaceCapabilitiesKHR surfaceCapabilities = VkSurfaceCapabilitiesKHR.calloc();

	private final Set<SwapchainResourceManager<?, ?>> associations = ConcurrentHashMap.newKeySet();
	private final List<SwapchainWrapper> oldSwapchains = new ArrayList<>();

	private SwapchainWrapper currentSwapchain;
	private int currentSwapchainID;
	int currentWidth, currentHeight;
	volatile WindowSize windowSize;
	volatile boolean needsToKnowWindowSize;

	SwapchainManager(SwapchainFunctions functions, WindowProperties properties, PresentModes presentModes) {
		this.functions = functions;
		this.properties = properties;
		this.presentModes = presentModes;
		this.acquireSemaphores = new AcquireSemaphores(functions, properties.title(), properties.maxFramesInFlight());
	}

	AcquiredImage2 acquire(int presentMode, boolean useFence) {
		updateSize();
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
		if (oldSwapchains.size() > properties.maxOldSwapchains()) {
			functions.deviceWaitIdle();
			for (var swapchain : oldSwapchains) swapchain.destroy();
			oldSwapchains.clear();
		}

		createSwapchain(presentMode);
	}

	private void createSwapchain(int presentMode) {
		updateSize();
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
		functions.getSurfaceCapabilities(surfaceCapabilities);
		int width = surfaceCapabilities.currentExtent().width();
		int height = surfaceCapabilities.currentExtent().height();
		var currentWindowSize = windowSize;

		if (width != -1) currentWidth = width;
		else if (currentWindowSize != null) currentWidth = currentWindowSize.width();
		if (height != -1) currentHeight = height;
		else if (currentWindowSize != null) currentHeight = currentWindowSize.height();

		boolean needsToKnowWindowSize = width == -1 || height == -1;
		if (needsToKnowWindowSize != this.needsToKnowWindowSize) {
			this.needsToKnowWindowSize = needsToKnowWindowSize;
		}
	}

	void destroy() {
		for (var association : associations) association.destroy();
		surfaceCapabilities.free();
		if (currentSwapchain != null || !oldSwapchains.isEmpty()) functions.deviceWaitIdle();
		for (SwapchainWrapper swapchain : oldSwapchains) swapchain.destroy();
		oldSwapchains.clear();
		if (currentSwapchain != null) currentSwapchain.destroy();
		acquireSemaphores.destroy();
	}
}
