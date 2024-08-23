package com.github.knokko.boiler.window;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.synchronization.AwaitableSubmission;
import org.lwjgl.system.MemoryStack;

import static java.lang.Thread.sleep;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwWindowShouldClose;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * A class to handle the render loop, and optionally the event loop, for a single window. This class can either be used
 * in isolation, or in combination with <i>WindowEventLoop</i> for smooth resizing and/or multiple windows. See
 * docs/swapchain.md for ore information.
 */
public abstract class WindowRenderLoop {

	protected final VkbWindow window;
	protected final int numFramesInFlight;
	protected boolean acquireSwapchainImageWithFence;
	protected int presentMode;
	private volatile boolean didStart;

	/**
	 * @param window The window
	 * @param numFramesInFlight The number of frames in-flight that will be used for rendering, which determines the
	 *                          <i>frameIndex</i> parameter that will be supplied to <i>renderFrame</i>
	 * @param acquireSwapchainImageWithFence <i>true</i> when swapchain images should be acquired using a 'ready fence',
	 *                                       <i>false</i> when swapchain images should be acquired using a
	 *                                       'ready semaphore'
	 * @param presentMode The initial present mode of the initial swapchain. You can change this whenever you want.
	 */
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

	/**
	 * Starts the render loop. If the window has been added to a <i>WindowEventLoop</i>, the rendering will happen
	 * in another thread. If not, the render/event loop will happen on this thread, and this method will block until
	 * the window has been closed.<br>
	 *
	 * Note: you should <b>not</b> call this method yourself when you have added this loop via
	 * <i>WindowEventLoop.addWindow</i> because that method will automatically start this render loop.
	 */
	public void start() {
		if (didStart) throw new IllegalStateException("This loop already started");
		didStart = true;

		if (window.windowLoop == null) this.run();
		else new Thread(this::run).start();
	}

	/**
	 * This method will be called once before the actual rendering starts, and should be used to create the resources
	 * that are needed for rendering.
	 */
	protected abstract void setup(BoilerInstance instance);

	/**
	 * This method should render onto the acquired swapchain image.
	 * @param stack A <i>MemoryStack</i> that can be used during this frame to allocate objects on
	 * @param frameIndex The index into the frame-in-flight-resource arrays. The render loop will increment a
	 *                   <i>counter</i> every frame, and <i>frameIndex = counter % numFramesInFlight</i>
	 * @param acquiredImage The swapchain image that has been acquired. This will <b>not</b> be <i>null</i>
	 * @param instance The VkBoiler instance
	 * @return The last queue submission that renders onto the swapchain image. Hint: <i>VkbQueue.submit</i> will return
	 * an <i>AwaitableSubmission</i> when you provide a non-null fence.
	 */
	protected abstract AwaitableSubmission renderFrame(
			MemoryStack stack, int frameIndex, AcquiredImage acquiredImage, BoilerInstance instance
	);

	/**
	 * This method will be called after the render loop has finished, and should be used to wait on all your fences,
	 * and then destroy all resources that you created during <i>setup</i>
	 */
	protected abstract void cleanUp(BoilerInstance instance);
}
