package com.github.knokko.boiler.window;

import org.lwjgl.sdl.SDL_Event;

import java.util.concurrent.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.sdl.SDLEvents.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * A class to handle GLFW or SDL events on the main thread while rendering is happening on
 * other threads. See docs/swapchain.md for more information.
 */
public class WindowEventLoop {
	// TODO Update docs/swapchain.md

	private final ConcurrentHashMap<VkbWindow, State> stateMap = new ConcurrentHashMap<>();
	private final Runnable updateCallback;
	private final double waitTimeout;

	private boolean useSDL;

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

	private void updateWindows() {
		var iterator = stateMap.entrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			VkbWindow window = entry.getKey();
			State state = entry.getValue();

			window.updateSize();
			if (!state.initialized) {
				window.registerCallbacks();
				state.initialized = true;
			}

			if (state.renderLoop.thread != null && !state.renderLoop.thread.isAlive()) {
				window.destroy();
				iterator.remove();
			}
		}
	}

	/**
	 * Adds the given window (render loop) to the event loop. After this method returns, this event loop will
	 * handle swapchain recreations for the given window. This method can be called from any thread.
	 */
	public void addWindow(WindowRenderLoop renderLoop) {
		useSDL = renderLoop.window.instance.useSDL;
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
				updateWindows();
			}
		}
	}

	private static class State {

		final WindowRenderLoop renderLoop;
		boolean initialized;

		State(WindowRenderLoop renderLoop) {
			this.renderLoop = renderLoop;
		}
	}
}
