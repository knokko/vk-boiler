package com.github.knokko.boiler.window;

import com.github.knokko.boiler.BoilerInstance;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.system.MemoryStack;

import static com.github.knokko.boiler.exceptions.SDLFailureException.assertSdlSuccess;
import static java.lang.Thread.sleep;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.sdl.SDLEvents.*;
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
	volatile Thread thread;
	private boolean sdlCloseRequested;

	/**
	 * @param window The window
	 * @param acquireSwapchainImageWithFence <i>true</i> when swapchain images should be acquired using a 'ready fence',
	 *                                       <i>false</i> when swapchain images should be acquired using a
	 *                                       'ready semaphore'
	 * @param presentMode The initial present mode of the initial swapchain. You can change this whenever you want.
	 */
	public WindowRenderLoop(
			VkbWindow window, boolean acquireSwapchainImageWithFence, int presentMode
	) {
		this.window = window;
		this.numFramesInFlight = window.properties.maxFramesInFlight();
		this.acquireSwapchainImageWithFence = acquireSwapchainImageWithFence;
		this.presentMode = presentMode;
		if (window.instance.useSDL) {
			assertSdlSuccess(SDL_AddEventWatch((userData, rawEvent) -> {
				if (SDL_Event.ntype(rawEvent) == SDL_EVENT_WINDOW_CLOSE_REQUESTED &&
						nSDL_GetWindowFromEvent(rawEvent) == window.properties.handle()) sdlCloseRequested = true;
				return false;
			}, 0L), "AddEventWatch");
		}
	}

	private void run() {
		try (var stack = stackPush()) {
			setup(window.instance, stack);
		} catch (Throwable setupFailed) {
			window.destroy();
			throw setupFailed;
		}

		try {
			long currentFrame = 0;
			while (!sdlCloseRequested && (window.instance.useSDL || !glfwWindowShouldClose(window.properties.handle()))) {
				if (window.windowLoop == null) {
					try (var stack = stackPush()) {
						if (window.instance.useSDL) {
							var event = SDL_Event.calloc(stack);
							//noinspection StatementWithEmptyBody
							while (SDL_PollEvent(event)) {
								// Users should use SDL_AddEventWatch to listen for events
							}
						} else {
							glfwPollEvents();
						}
						window.updateSize();
					}
				}

				int frameIndex = (int) (currentFrame % numFramesInFlight);

				try (var stack = stackPush()) {
					AcquiredImage acquiredImage;
					if (acquireSwapchainImageWithFence) {
						acquiredImage = window.acquireSwapchainImageWithFence(presentMode);
					} else acquiredImage = window.acquireSwapchainImageWithSemaphore(presentMode);
					if (acquiredImage == null) {
						//noinspection BusyWait
						sleep(100);
						continue;
					}

					if (acquireSwapchainImageWithFence) acquiredImage.acquireSubmission.awaitCompletion();

					renderFrame(stack, frameIndex, acquiredImage, window.instance);
					window.presentSwapchainImage(acquiredImage);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
				currentFrame += 1;
			}
		} finally {
			try {
				cleanUp(window.instance);
				window.destroy();
			} catch (Throwable cleanUpFailed) {
				// The purpose of this catch block is to ensure that errors during cleanUp don't suppress the
				// more important errors encountered in the try block
				System.err.println("Failed to clean-up WindowRenderLoop:");
				//noinspection CallToPrintStackTrace
				cleanUpFailed.printStackTrace();
			}
		}
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

		if (window.windowLoop == null) {
			window.registerCallbacks();
			this.run();
		} else {
			this.thread = new Thread(this::run);
			thread.setDaemon(true); // Ensure that the render thread dies when the main thread dies (unexpectedly)
			thread.start();
		}
	}

	/**
	 * This method will be called once before the actual rendering starts, and should be used to create the resources
	 * that are needed for rendering.
	 */
	protected abstract void setup(BoilerInstance instance, MemoryStack stack);

	/**
	 * This method should render onto the acquired swapchain image.
	 * @param stack A <i>MemoryStack</i> that can be used during this frame to allocate objects on
	 * @param frameIndex The index into the frame-in-flight-resource arrays. The render loop will increment a
	 *                   <i>counter</i> every frame, and <i>frameIndex = counter % numFramesInFlight</i>
	 * @param acquiredImage The swapchain image that has been acquired. This will <b>not</b> be <i>null</i>
	 * @param instance The VkBoiler instance
	 */
	protected abstract void renderFrame(
			MemoryStack stack, int frameIndex, AcquiredImage acquiredImage, BoilerInstance instance
	);

	/**
	 * This method will be called after the render loop has finished, and should be used to wait on all your fences,
	 * and then destroy all resources that you created during <i>setup</i>
	 */
	protected abstract void cleanUp(BoilerInstance instance);
}
