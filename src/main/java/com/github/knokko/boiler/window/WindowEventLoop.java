package com.github.knokko.boiler.window;

import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.system.Platform;

import java.util.concurrent.*;

import static com.github.knokko.boiler.exceptions.SDLFailureException.assertSdlSuccess;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.sdl.SDLEvents.*;
import static org.lwjgl.sdl.SDLVideo.SDL_DestroyWindow;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * A class to handle GLFW or SDL events on the main thread while rendering is happening on
 * other threads. See docs/swapchain.md for more information.
 */
public class WindowEventLoop {
	// TODO Update docs/swapchain.md

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

	private void update() {
		Task task;
		task = queue.poll();

		while (task != null) {
			if (task.runnable == null) {
				if (useSDL) {
					SDL_DestroyWindow(task.window.properties.handle());
				} else {
					glfwDestroyWindow(task.window.properties.handle());
				}
				stateMap.remove(task.window);
			} else throw new RuntimeException("TODO IMPOSSIBLE?");

			task = queue.poll();
		}
	}

	private void initializeAndDestroyWindows() {
		stateMap.forEach((window, state) -> {
			window.updateSize();
			if (!state.initialized.isDone()) {
				window.registerCallbacks();
				state.initialized.complete(null);
			}
			if (state.renderLoop.thread != null && !state.renderLoop.thread.isAlive()) {
				// TODO Close window?
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
			try (var stack = stackPush()) {
				if (useSDL) {
					var event = SDL_Event.calloc(stack);
					if (waitTimeout > 0.0) SDL_WaitEventTimeout(event, (int) (1000 * waitTimeout));
					else SDL_WaitEvent(event);
					//noinspection StatementWithEmptyBody
					while (SDL_PollEvent(event)) {
						// Users can respond to events by using SDL_AddEventWatcher
					}
				} else {
					if (waitTimeout > 0.0) glfwWaitEventsTimeout(waitTimeout);
					else glfwWaitEvents();
				}
				if (updateCallback != null) updateCallback.run();
				update();
				initializeAndDestroyWindows();
			}
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

	private record Task(Runnable runnable, State state, VkbWindow window) {
	}

	private static class State {

		final WindowRenderLoop renderLoop;
		final CompletableFuture<Object> initialized = new CompletableFuture<>();

		State(WindowRenderLoop renderLoop) {
			this.renderLoop = renderLoop;
		}
	}
}
