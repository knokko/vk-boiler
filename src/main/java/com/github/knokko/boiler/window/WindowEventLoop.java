package com.github.knokko.boiler.window;

import org.lwjgl.system.Platform;

import java.util.concurrent.*;

import static org.lwjgl.glfw.GLFW.*;

/**
 * A class to handle swapchain recreations and GLFW events on the main thread while rendering is happening on other
 * threads. See docs/swapchain.md for more information.
 */
public class WindowEventLoop {

	private final BlockingQueue<Task> queue = new LinkedBlockingQueue<>();
	private final ConcurrentHashMap<VkbWindow, State> stateMap = new ConcurrentHashMap<>();
	private final Runnable updateCallback;
	private final double waitTimeout;

	/**
	 * @param waitTimeout The timeout (in seconds) that will be passed to each call to <i>glfwWaitEventsTimeout</i>.
	 *                    When this is 0, the event loop will call <i>glfwWaitEvents</i> instead.
	 * @param updateCallback An optional callback that will be called every time after <i>glfwWaitEvents</i> or
	 *                       <i>glfwWaitEventsTimeout</i>. You can use this if you need to occasionally run some code
	 *                       on the main thread. Note that you can use <i>glfwPostEmptyEvent</i> from another thread to
	 *                       cause <i>glfwWaitEvents(Timeout)</i> to return early.
	 */
	public WindowEventLoop(double waitTimeout, Runnable updateCallback) {
		this.updateCallback = updateCallback;
		this.waitTimeout = waitTimeout;
	}

	public WindowEventLoop() {
		this(0.0, null);
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
			// resizing after glfwWaitEvents() returns.
			if (Platform.get() != Platform.WINDOWS) return;

			try {
				task = queue.poll(250, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		} else task = queue.poll();

		while (task != null) {
			if (task.runnable == null) {
				glfwDestroyWindow(task.window.glfwWindow);
				stateMap.remove(task.window);
			} else task.runnable.run();

			task.state.shouldCheckResize = false;
			task.state.lastResizeTime = System.nanoTime();
			task.state.resizeCompleted.release();

			task = queue.poll();
		}
	}

	private void initializeNewWindows() {
		stateMap.forEach((window, state) -> {
			if (!state.initialized.isDone()) {
				//noinspection resource
				glfwSetFramebufferSizeCallback(window.glfwWindow, (glfwWindow, width, height) -> update(window));
				state.initialized.complete(null);
			}
		});
	}

	private void addWindow(VkbWindow window) {
		stateMap.put(window, new State());
		window.windowLoop = this;
	}

	/**
	 * Adds the given window (render loop) to the event loop. After this method returns, this event loop will
	 * handle swapchain recreations for the given window. This method can be called from any thread.
	 */
	public void addWindow(WindowRenderLoop renderLoop) {
		addWindow(renderLoop.window);
		renderLoop.start();
	}

	/**
	 * Runs the event loop. This method must be called on the <b>main</b> thread, and it will block until all
	 * windows are destroyed.
	 */
	public void runMain() {
		while (!stateMap.isEmpty()) {
			if (waitTimeout > 0.0) glfwWaitEventsTimeout(waitTimeout);
			else glfwWaitEvents();
			if (updateCallback != null) updateCallback.run();
			update(null);
			initializeNewWindows();
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

	void destroy(VkbWindow window) {
		queueResize(null, window);
	}

	void queueResize(Runnable resize, VkbWindow window) {
		var state = getState(window);
		queue.add(new Task(resize, state, window));
		glfwPostEmptyEvent();
		state.resizeCompleted.acquireUninterruptibly();
	}

	private record Task(Runnable runnable, State state, VkbWindow window) {
	}

	private static class State {

		final CompletableFuture<Object> initialized = new CompletableFuture<>();
		final Semaphore resizeCompleted = new Semaphore(0);
		volatile boolean shouldCheckResize;
		volatile long lastResizeTime;
	}
}
