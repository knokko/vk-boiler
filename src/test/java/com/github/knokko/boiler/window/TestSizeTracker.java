package com.github.knokko.boiler.window;

import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;

import static org.junit.jupiter.api.Assertions.*;

public class TestSizeTracker {

	@Test
	public void testWithoutMainThread() {
		DummySwapchainFunctions functions = new DummySwapchainFunctions();
		functions.capabilities = VkSurfaceCapabilitiesKHR.create();
		SizeTracker tracker = new SizeTracker(functions, VkSurfaceCapabilitiesKHR.calloc());

		functions.capabilities.currentExtent().set(20, 30);
		tracker.update();
		assertFalse(tracker.needsWindowSizeFromMainThread());
		assertEquals(20, tracker.getWindowWidth());
		assertEquals(30, tracker.getWindowHeight());

		tracker.update();
		assertFalse(tracker.needsWindowSizeFromMainThread());
		assertEquals(20, tracker.getWindowWidth());
		assertEquals(30, tracker.getWindowHeight());

		functions.capabilities.currentExtent().set(100, 70);
		assertFalse(tracker.needsWindowSizeFromMainThread());
		assertEquals(100, tracker.getWindowWidth());
		assertEquals(70, tracker.getWindowHeight());

	}

	@Test
	public void testWithMainThread() {
		DummySwapchainFunctions functions = new DummySwapchainFunctions();
		functions.capabilities = VkSurfaceCapabilitiesKHR.create();
		SizeTracker tracker = new SizeTracker(functions, VkSurfaceCapabilitiesKHR.calloc());

		tracker.update();
		assertFalse(tracker.needsWindowSizeFromMainThread());
		assertEquals(0, tracker.getWindowWidth());
		assertEquals(0, tracker.getWindowHeight());

		functions.capabilities.currentExtent().set(-1, -1);
		tracker.update();
		assertTrue(tracker.needsWindowSizeFromMainThread());
		assertEquals(0, tracker.getWindowWidth());
		assertEquals(0, tracker.getWindowHeight());

		tracker.setWindowSizeFromMainThread(100, 200);
		assertTrue(tracker.needsWindowSizeFromMainThread());
		assertEquals(0, tracker.getWindowWidth());
		assertEquals(0, tracker.getWindowHeight());

		tracker.update();
		assertTrue(tracker.needsWindowSizeFromMainThread());
		assertEquals(100, tracker.getWindowWidth());
		assertEquals(200, tracker.getWindowHeight());

		tracker.setWindowSizeFromMainThread(105, 195);
		tracker.update();
		assertEquals(105, tracker.getWindowWidth());
		assertEquals(195, tracker.getWindowHeight());
	
	}
}

