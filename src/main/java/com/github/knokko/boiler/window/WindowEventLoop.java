package com.github.knokko.boiler.window;

import com.github.knokko.boiler.exceptions.SDLFailureException;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.system.Platform;

import java.util.concurrent.*;

import static com.github.knokko.boiler.exceptions.SDLFailureException.assertSdlSuccess;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.sdl.SDLError.SDL_GetError;
import static org.lwjgl.sdl.SDLEvents.*;
import static org.lwjgl.sdl.SDLVideo.SDL_DestroyWindow;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * A class to handle swapchain recreations and GLFW or SDL events on the main thread while rendering is happening on
 * other threads. See docs/swapchain.md for more information.
 */
public class WindowEventLoop {

	private final BlockingQueue<Task> queue = new LinkedBlockingQueue<>();
	private final ConcurrentHashMap<VkbWindow, State> stateMap = new ConcurrentHashMap<>();
	private final Runnable updateCallback;
	private final double waitTimeout;

	private boolean useSDL;
	private int emptyEventType;

	/**
	 * @param waitTimeout The timeout (in seconds) that will be passed to each call to <i>glfwWaitEventsTimeout</i> or
	 *                       <i>SDL_WaitEventTimeout</i>. When this is 0, the event loop will call
	 *                       <i>glfwWaitEvents</i> or <i>SDL_WaitEvent</i> instead.
	 * @param updateCallback An optional callback that will be called every time after <i>glfwWaitEvents</i>,
	 *                       <i>glfwWaitEventsTimeout</i>, <i>SDL_WaitEvent</i>, or <i>SDL_WaitEventTimeout</i>.
	 *                       You can use this if you need to occasionally run some code on the main thread.
	 *                       Note that you can use <i>glfwPostEmptyEvent</i> or <i>SDL_PushEvent</i> from another
	 *                       thread to cause <i>glfwWaitEvents(Timeout)</i> or <i>SDL_WaitEvent(Timeout)</i> to
	 *                       return early.
	 */
	public WindowEventLoop(double waitTimeout, Runnable updateCallback) {
		this.updateCallback = updateCallback;
		this.waitTimeout = waitTimeout;
	}

	public WindowEventLoop() {
		this(0.5, null);
	}

	private void update(VkbWindow resizedWindow) {
		Task task;
		if (resizedWindow != null) {
			// For some reason, my computer may freeze completely if I recreate a swapchain of a window while another
			// window of this process is hidden behind any other window. To avoid this problem, recreating swapchains
			// during the glfwFramebufferSizeCallback is only allowed when this process has only 1 window.
			if (stateMap.size() != 1) return;

			// Notify the window that it should probably resize. This is required for proper resizing on Wayland
			// because Wayland swapchains are never out-of-date or suboptimal.
			getState(resizedWindow).shouldCheckResize = true;

			// Due to the stupid win32 event loop, Windows is the only OS that can only resize smoothly when
			// the swapchain is recreated during the resize callback. On all other platforms, we can simply do the
			// resizing after glfwWaitEvents() or SDL_WaitEvent returns.
			if (Platform.get() != Platform.WINDOWS) return;

			try {
				task = queue.poll(250, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		} else task = queue.poll();

		while (task != null) {
			if (task.runnable == null) {
				if (useSDL) {
					SDL_DestroyWindow(task.window.handle);
				} else {
					glfwDestroyWindow(task.window.handle);
				}
				stateMap.remove(task.window);
			} else task.runnable.run();

			task.state.shouldCheckResize = false;
			task.state.lastResizeTime = System.nanoTime();
			task.state.actionCompleted.release();

			task = queue.poll();
		}
	}

	private void initializeAndDestroyWindows() {
		stateMap.forEach((window, state) -> {
			if (!state.initialized.isDone()) {
				if (useSDL) {
					assertSdlSuccess(SDL_AddEventWatch((userData, rawEvent) -> {
						if (SDL_Event.ntype(rawEvent) == SDL_EVENT_WINDOW_PIXEL_SIZE_CHANGED &&
								nSDL_GetWindowFromEvent(rawEvent) == window.handle) update(window);
						return false;
					}, 0L), "AddEventWatch");
				} else {
					//noinspection resource
					glfwSetFramebufferSizeCallback(window.handle, (glfwWindow, width, height) -> update(window));
				}
				state.initialized.complete(null);
			}
			if (state.renderLoop.thread != null && !state.renderLoop.thread.isAlive()) {
				queue.add(new Task(null, state, window));
			}
		});
	}

	/**
	 * Adds the given window (render loop) to the event loop. After this method returns, this event loop will
	 * handle swapchain recreations for the given window. This method can be called from any thread.
	 */
	public void addWindow(WindowRenderLoop renderLoop) {
		useSDL = renderLoop.window.instance.useSDL;
		if (useSDL) emptyEventType = SDL_RegisterEvents(1);
		stateMap.put(renderLoop.window, new State(renderLoop));
		renderLoop.window.windowLoop = this;
		renderLoop.start();
	}

	/**
	 * Runs the event loop. This method must be called on the <b>main</b> thread, and it will block until all
	 * windows are destroyed.
	 */
	public void runMain() {
		while (!stateMap.isEmpty()) {
			if (useSDL) {
				try (var stack = stackPush()) {
					var event = SDL_Event.calloc(stack);
					if (waitTimeout > 0.0) SDL_WaitEventTimeout(event, (int) (1000 * waitTimeout));
					else SDL_WaitEvent(event);
					//noinspection StatementWithEmptyBody
					while (SDL_PollEvent(event)) {
						// Users can respond to events by using SDL_AddEventWatcher
					}
				}
			} else {
				if (waitTimeout > 0.0) glfwWaitEventsTimeout(waitTimeout);
				else glfwWaitEvents();
			}
			if (updateCallback != null) updateCallback.run();
			update(null);
			initializeAndDestroyWindows();
		}
	}

	private State getState(VkbWindow window) {
		State state = stateMap.get(window);

		if (state == null)
			throw new IllegalArgumentException("window " + window + " wasn't added using this.addWindow");
		if (!state.initialized.isDone()) {
			try {
				state.initialized.get();
			} catch (InterruptedException | ExecutionException e) {
				throw new RuntimeException(e);
			}
		}

		return state;
	}

	boolean shouldCheckResize(VkbWindow window) {
		return getState(window).shouldCheckResize;
	}

	void queueMainThreadAction(Runnable resize, VkbWindow window) {
		var state = getState(window);
		queue.add(new Task(resize, state, window));
		if (useSDL) {
			try (var stack = stackPush()) {
				var event = SDL_Event.calloc(stack);
				event.type(emptyEventType);
				assertSdlSuccess(SDL_PushEvent(event), "PushEvent");
			}
		} else glfwPostEmptyEvent();
		state.actionCompleted.acquireUninterruptibly();
	}

	private record Task(Runnable runnable, State state, VkbWindow window) {
	}

	private static class State {

		final WindowRenderLoop renderLoop;
		final CompletableFuture<Object> initialized = new CompletableFuture<>();
		final Semaphore actionCompleted = new Semaphore(0);
		volatile boolean shouldCheckResize;
		volatile long lastResizeTime;

		State(WindowRenderLoop renderLoop) {
			this.renderLoop = renderLoop;
		}
	}
}
