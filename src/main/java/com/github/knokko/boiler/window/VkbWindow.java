package com.github.knokko.boiler.window;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.memory.callbacks.CallbackUserData;
import com.github.knokko.boiler.queues.VkbQueueFamily;
import org.lwjgl.sdl.SDL_Event;

import java.nio.IntBuffer;
import java.util.*;

import static com.github.knokko.boiler.exceptions.SDLFailureException.assertSdlSuccess;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.sdl.SDLEvents.*;
import static org.lwjgl.sdl.SDLVideo.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSurface.*;

/**
 * A thick wrapper around a GLFW window or SDL window, and a potential manager for the swapchains of the window. See
 * docs/swapchain.md for instructions on how to use this.
 */
public class VkbWindow {

	BoilerInstance instance;

	public final WindowProperties properties;

	/**
	 * The queue family that will be used to present images of the swapchains for this window, typically the
	 * 'main' graphics queue family
	 */
	public final VkbQueueFamily presentFamily;
	private final PresentModes presentModes;
	private final ShowCounter showCounter;

	volatile boolean showFromMainThread;

	private boolean hasBeenDestroyed;

	WindowEventLoop windowLoop;

	private SwapchainManager swapchains;

	/**
	 * This constructor is meant for internal use only. You should use <i>BoilerBuilder.addWindow</i> or
	 * <i>BoilerInstance.addWindow</i> instead
	 */
	public VkbWindow(
			WindowProperties properties, VkbQueueFamily presentFamily,
			Collection<Integer> supportedPresentModes, Set<Integer> preparedPresentModes
	) {
		this.properties = properties;
		this.presentModes = new PresentModes(supportedPresentModes, preparedPresentModes);
		this.presentFamily = presentFamily;
		this.showCounter = new ShowCounter(properties.numHiddenFrames());
	}

	// TODO KHR swapchain maintenance instead of EXT

	/**
	 * This method is meant for internal use only. Expect an {@code IllegalStateException} if you call it yourself.
	 */
	public void setInstance(BoilerInstance instance) {
		if (swapchains != null) throw new IllegalStateException();

		SwapchainFunctions functions = new RealSwapchainFunctions(instance, presentFamily, properties);
		this.swapchains = new SwapchainManager(functions, properties, presentModes);
		this.instance = instance;
	}

	/**
	 * An immutable set of all supported <i>VkPresentModeKHR</i>s of the <i>VkSurfaceKHR</i> of this window.
	 */
	public Set<Integer> getSupportedPresentModes() {
		return presentModes.supported;
	}

	/**
	 * Acquires a swapchain image that will be available after waiting on its <i>acquireFence</i>
	 * @param presentMode The present mode that will be used to present the swapchain image
	 * @return The acquired swapchain image, or null if no image can be acquired now
	 * (e.g. because the window is minimized)
	 */
	public AcquiredImage2 acquireSwapchainImageWithFence(int presentMode) {
		return acquireSwapchainImage(presentMode, true);
	}

	/**
	 * Acquires a swapchain image that will be available after its <i>acquireSemaphore</i> has
	 * been signaled.
	 * @param presentMode The present mode that will be used to present the swapchain image
	 * @return The acquired swapchain image, or null if no image can be acquired now
	 * (e.g. because the window is minimized)
	 */
	public AcquiredImage2 acquireSwapchainImageWithSemaphore(int presentMode) {
		return acquireSwapchainImage(presentMode, false);
	}

	private void assertMainThread() {
		if (!Thread.currentThread().getName().equals("main")) throw new Error("updateSize must happen on main thread");
	}

	/**
	 * @return The current (or very recent) width of the window, in pixels
	 */
	public int getWidth() {
		return swapchains.getWidth();
	}

	/**
	 * @return The current (or very recent) height of the window, in pixels
	 */
	public int getHeight() {
		return swapchains.getHeight();
	}

	private AcquiredImage2 acquireSwapchainImage(int presentMode, boolean useAcquireFence) {
		instance.checkForFatalValidationErrors();
		return swapchains.acquire(presentMode, useAcquireFence);
	}

	void showWindowNow() {
		assertMainThread();
		if (instance.useSDL) {
			assertSdlSuccess(SDL_ShowWindow(properties.handle()), "ShowWindow");
		} else glfwShowWindow(properties.handle());
	}

	public void registerCallbacks() {
		if (instance.useSDL) {
			assertSdlSuccess(SDL_AddEventWatch((userData, rawEvent) -> {
				if (SDL_Event.ntype(rawEvent) == SDL_EVENT_WINDOW_PIXEL_SIZE_CHANGED &&
						nSDL_GetWindowFromEvent(rawEvent) == properties.handle()) updateSize();
				return false;
			}, 0L), "AddEventWatch");
		} else {
			//noinspection resource
			glfwSetFramebufferSizeCallback(
					properties.handle(), (glfwWindow, width, height) -> updateSize()
			);
		}
	}

	public void updateSize() {
		try (var stack = stackPush()) {
			IntBuffer pWidth = stack.callocInt(1);
			IntBuffer pHeight = stack.callocInt(1);
			if (instance.useSDL) {
				if ((SDL_GetWindowFlags(properties.handle()) & SDL_WINDOW_MINIMIZED) == 0) {
					assertSdlSuccess(SDL_GetWindowSizeInPixels(
							properties.handle(), pWidth, pHeight
					), "GetWindowSizeInPixels");
				}
			} else {
				if (glfwGetWindowAttrib(properties.handle(), GLFW_ICONIFIED) == GLFW_FALSE) {
					glfwGetFramebufferSize(properties.handle(), pWidth, pHeight);
				}
			}
			swapchains.setWindowSizeFromMainThread(pWidth.get(0), pHeight.get(0));
		}
	}

	/**
	 * Presents a previously acquired swapchain image
	 * @param image The swapchain image
	 */
	public void presentSwapchainImage(AcquiredImage2 image) {
		instance.checkForFatalValidationErrors();
		image.swapchain.presentImage(image);
		if (showCounter.shouldShowNow()) {
			if (windowLoop == null) showWindowNow();
			else showFromMainThread = true;
		}
	}

	/**
	 * Requests GLFW or SDL to close this window. This should cause this window to close after at most 1 frame.
	 */
	public void requestClose() {
		if (instance.useSDL) {
			try (var stack = stackPush()) {
				var event = SDL_Event.calloc(stack);
				event.type(SDL_EVENT_WINDOW_CLOSE_REQUESTED);
				event.window().windowID(SDL_GetWindowID(properties.handle()));
				SDL_PushEvent(event);
			}
		} else {
			glfwSetWindowShouldClose(properties.handle(), true);
		}
	}

	/**
	 * Destroys this window, its surface, and its swapchains (unless it has already been destroyed). It is safe to call
	 * this method more than once, and even from multiple threads at the same time, but no other method calls must be
	 * pending.<br>
	 *
	 * Note: if this window was created using `BoilerBuilder.addWindow`, this window will automatically be destroyed
	 * during `BoilerInstance.destroyInitialObjects`.<br>
	 *
	 * Note: if you are using a `(Simple)WindowRenderLoop` to manage this window, it will automatically be destroyed
	 * after <i>glfwWindowShouldClose</i> returns <i>true</i>, or SDL receives a similar event.
	 */
	public synchronized void destroy() {
		if (hasBeenDestroyed) return;

		try (var stack = stackPush()) {
			swapchains.destroy();
			vkDestroySurfaceKHR(instance.vkInstance(), properties.vkSurface(), CallbackUserData.SURFACE.put(stack, instance));
		} finally {
			if (windowLoop == null) destroyHandle();
			hasBeenDestroyed = true;
		}
	}

	void destroyHandle() {
		if (instance.useSDL) SDL_DestroyWindow(properties.handle());
		else glfwDestroyWindow(properties.handle());
	}
}
