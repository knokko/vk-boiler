package com.github.knokko.boiler.window;

import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;

class AcquireSemaphores {

	private final SwapchainFunctions functions;
	private final String title;
	private final long[] semaphores;

	private int currentFrame;

	AcquireSemaphores(SwapchainFunctions functions, String title, int maxFramesInFlight) {
		this.functions = functions;
		this.title = title;
		this.semaphores = new long[maxFramesInFlight];
	}

	long next() {
		if (semaphores[currentFrame] == VK_NULL_HANDLE) {
			semaphores[currentFrame] = functions.borrowSemaphore(title + "Acquire" + currentFrame);
		}
		long next = semaphores[currentFrame];
		currentFrame = (currentFrame + 1) % semaphores.length;
		return next;
	}

	void destroy() {
		for (long semaphore : semaphores) {
			if (semaphore != VK_NULL_HANDLE) functions.returnSemaphore(semaphore);
		}
	}
}
