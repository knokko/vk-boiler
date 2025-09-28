package com.github.knokko.boiler.window;

import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;

class PresentSemaphores {

	private final SwapchainFunctions functions;
	private final String debugName;
	private final long[] semaphores;

	PresentSemaphores(SwapchainFunctions functions, String debugName, int numImages) {
		this.functions = functions;
		this.debugName = debugName;
		this.semaphores = new long[numImages];
	}

	long get(int imageIndex) {
		if (semaphores[imageIndex] == VK_NULL_HANDLE) {
			semaphores[imageIndex] = functions.borrowSemaphore(debugName + "Present" + imageIndex);
		}
		return semaphores[imageIndex];
	}

	void destroy() {
		for (long semaphore : semaphores) {
			if (semaphore != VK_NULL_HANDLE) functions.returnSemaphore(semaphore);
		}
	}
}
