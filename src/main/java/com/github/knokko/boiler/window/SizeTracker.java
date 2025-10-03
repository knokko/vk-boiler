package com.github.knokko.boiler.window;

import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;

class SizeTracker {

	private final SwapchainFunctions functions;
	private final VkSurfaceCapabilitiesKHR capabilities;
	private int windowWidth, windowHeight;

	private volatile boolean needsWindowSizeFromMainThread;
	private volatile WindowSize windowSizeFromMainThread;

	SizeTracker(SwapchainFunctions functions, VkSurfaceCapabilitiesKHR capabilities) {
		this.functions = functions;
		this.capabilities = capabilities;
	}

	int getWindowWidth() {
		return windowWidth;
	}

	int getWindowHeight() {
		return windowHeight;
	}

	boolean canUseSwapchain(int swapchainWidth, int swapchainHeight) {
		return swapchainWidth == windowWidth && swapchainHeight == windowHeight;
	}

	boolean canCreateSwapchain() {
		return windowWidth > 0 && windowHeight > 0;
	}

	void update() {
		functions.getSurfaceCapabilities(capabilities);
		int newWidth = capabilities.currentExtent().width();
		int newHeight = capabilities.currentExtent().height();
		boolean needsWindowSizeFromMainThread;
		if (newWidth != -1 && newHeight != -1) {
			windowWidth = newWidth;
			windowHeight = newHeight;
			needsWindowSizeFromMainThread = false;
		} else {
			needsWindowSizeFromMainThread = true;
			var mainSize = windowSizeFromMainThread;
			if (mainSize != null) {
				windowWidth = mainSize.width();
				windowHeight = mainSize.height();
			}
		}

		if (needsWindowSizeFromMainThread != this.needsWindowSizeFromMainThread) {
			this.needsWindowSizeFromMainThread = needsWindowSizeFromMainThread;
		}
	}

	void setWindowSizeFromMainThread(int width, int height) {
		windowSizeFromMainThread = new WindowSize(width, height);
	}

	boolean needsWindowSizeFromMainThread() {
		return needsWindowSizeFromMainThread;
	}
}
