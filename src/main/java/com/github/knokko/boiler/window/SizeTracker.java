package com.github.knokko.boiler.window;

import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;

class SizeTracker {

	private final SwapchainFunctions functions;
	private final VkSurfaceCapabilitiesKHR capabilities;
	private int windowWidth, windowHeight;

	private volatile WindowSize windowSizeFromMainThread;
	private long nextSurfaceCapabilitiesQuery = Long.MIN_VALUE;

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

	void update() {

		// Rate-limit vkGetPhysicalDeviceSurfaceCapabilitiesKHR to at most once every 5 milliseconds,
		// since it costs about 0.2 milliseconds on my machine.
		// Before the rate limit, this function was the primary performance bottleneck when the framerate was high.
		long currentTime = System.nanoTime();
		if (currentTime >= nextSurfaceCapabilitiesQuery) {
			functions.getSurfaceCapabilities(capabilities);
			nextSurfaceCapabilitiesQuery = currentTime + 5_000_000L;
		}

		int newWidth = capabilities.currentExtent().width();
		int newHeight = capabilities.currentExtent().height();
		if (newWidth != -1 && newHeight != -1) {
			windowWidth = newWidth;
			windowHeight = newHeight;
		}

		var mainSize = windowSizeFromMainThread;
		if (mainSize != null) {
			if (mainSize.width() == 0 || mainSize.height() == 0) {
				windowWidth = 0;
				windowHeight = 0;
			} else if (newWidth == -1 || newHeight == -1) {
				windowWidth = mainSize.width();
				windowHeight = mainSize.height();
			}
		}
	}

	void setWindowSizeFromMainThread(int width, int height) {
		windowSizeFromMainThread = new WindowSize(width, height);
	}
}
