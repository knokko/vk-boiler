package com.github.knokko.boiler.window;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.sync.AwaitableSubmission;
import org.lwjgl.system.MemoryStack;

import static java.lang.Thread.sleep;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwWindowShouldClose;
import static org.lwjgl.system.MemoryStack.stackPush;

public abstract class WindowRenderLoop {

	protected final VkbWindow window;
	protected final int numFramesInFlight;
	protected boolean acquireSwapchainImageWithFence;
	protected int presentMode;

	public WindowRenderLoop(
			VkbWindow window, int numFramesInFlight, boolean acquireSwapchainImageWithFence, int presentMode
	) {
		this.window = window;
		this.numFramesInFlight = numFramesInFlight;
		this.acquireSwapchainImageWithFence = acquireSwapchainImageWithFence;
		this.presentMode = presentMode;
	}

	private void run() {
		setup(window.instance);

		long currentFrame = 0;
		while (!glfwWindowShouldClose(window.glfwWindow)) {
			if (window.windowLoop == null) glfwPollEvents();

			int frameIndex = (int) (currentFrame % numFramesInFlight);

			try (var stack = stackPush()) {
				AcquiredImage acquiredImage;
				if (acquireSwapchainImageWithFence) acquiredImage = window.acquireSwapchainImageWithFence(presentMode);
				else acquiredImage = window.acquireSwapchainImageWithSemaphore(presentMode);
				if (acquiredImage == null) {
					//noinspection BusyWait
					sleep(100);
					continue;
				}

				if (acquireSwapchainImageWithFence) acquiredImage.acquireFence.awaitSignal();

				var renderSubmission = renderFrame(stack, frameIndex, acquiredImage, window.instance);
				if (renderSubmission == null) throw new RuntimeException(
						"Submission must not be null, make sure to submit a fence or timeline signal semaphore"
				);
				window.presentSwapchainImage(acquiredImage, renderSubmission);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
			currentFrame += 1;
		}

		cleanUp(window.instance);
		window.destroy();
	}

	public void start() {
		if (window.windowLoop == null) this.run();
		else new Thread(this::run).start();
	}

	protected abstract void setup(BoilerInstance instance);

	protected abstract AwaitableSubmission renderFrame(
			MemoryStack stack, int frameIndex, AcquiredImage acquiredImage, BoilerInstance instance
	);

	protected abstract void cleanUp(BoilerInstance instance);
}
