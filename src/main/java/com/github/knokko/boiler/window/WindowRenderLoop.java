package com.github.knokko.boiler.window;

import com.github.knokko.boiler.sync.AwaitableSubmission;
import org.lwjgl.system.MemoryStack;

import static java.lang.Thread.sleep;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwWindowShouldClose;
import static org.lwjgl.system.MemoryStack.stackPush;

public class WindowRenderLoop {

	private final VkbWindow window;
	public volatile boolean useAcquireFence;
	public volatile int presentMode;
	private final int numFramesInFlight;
	private final RenderFunction renderFunction;
	private final Runnable destructor;

	public WindowRenderLoop(
			VkbWindow window, boolean useAcquireFence, int presentMode,
			int numFramesInFlight, RenderFunction renderFunction, Runnable destructor
	) {
		this.window = window;
		this.useAcquireFence = useAcquireFence;
		this.presentMode = presentMode;
		this.numFramesInFlight = numFramesInFlight;
		this.renderFunction = renderFunction;
		this.destructor = destructor;
	}

	private void run() {
		long currentFrame = 0;
		while (!glfwWindowShouldClose(window.glfwWindow)) {
			if (window.windowLoop == null) glfwPollEvents();

			int frameIndex = (int) (currentFrame % numFramesInFlight);

			try (var stack = stackPush()) {
				AcquiredImage acquiredImage;
				if (useAcquireFence) acquiredImage = window.acquireSwapchainImageWithFence(presentMode);
				else acquiredImage = window.acquireSwapchainImageWithSemaphore(presentMode);
				if (acquiredImage == null) {
					//noinspection BusyWait
					sleep(100);
					continue;
				}

				if (useAcquireFence) acquiredImage.acquireFence.awaitSignal();

				var renderSubmission = renderFunction.renderFrame(stack, frameIndex, acquiredImage);
				if (renderSubmission == null) throw new RuntimeException(
						"Submission must not be null, make sure to submit a fence or timeline signal semaphore"
				);
				window.presentSwapchainImage(acquiredImage, renderSubmission);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
			currentFrame += 1;
		}

		destructor.run();
		window.destroy();
	}

	public void start() {
		if (window.windowLoop == null) this.run();
		else new Thread(this::run).start();
	}

	@FunctionalInterface
	public interface RenderFunction {

		AwaitableSubmission renderFrame(MemoryStack stack, int frameIndex, AcquiredImage acquiredImage);
	}
}
