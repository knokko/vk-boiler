package com.github.knokko.boiler.window;

import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;

import java.util.ArrayList;
import java.util.List;

import static com.github.knokko.boiler.utilities.CollectionHelper.createSet;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR;
import static org.lwjgl.vulkan.VK10.*;

public class TestSwapchainManager {

	private List<Long> createList(long... elements) {
		List<Long> list = new ArrayList<>();
		for (long element : elements) list.add(element);
		return list;
	}

	@Test
	public void testWithoutMaintenanceAndWith0() {
		var presentModes = new PresentModes(createSet(VK_PRESENT_MODE_FIFO_KHR), createSet(VK_PRESENT_MODE_FIFO_KHR));
		var properties = new WindowProperties(
				1234L, "TestTitle", 12345L, 2, VK_FORMAT_R8G8B8A8_UNORM,
				VK_COLOR_SPACE_SRGB_NONLINEAR_KHR, VK_IMAGE_USAGE_TRANSFER_DST_BIT,
				VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR, false, 0, 2
		);

		var functions = new DummySwapchainFunctions();
		functions.capabilities = VkSurfaceCapabilitiesKHR.create();
		functions.properties = properties;
		functions.numSwapchainImages = 3;

		var swapchains = new SwapchainManager(functions, properties, presentModes);

		functions.expectedSwapchain = 12L;
		functions.expectedAcquireSemaphore = 1L;
		functions.expectedAcquireFence = null;
		functions.nextImageIndex = 1;
		functions.nextAcquireResult = VK_SUCCESS;

		functions.capabilities.currentExtent().set(600, 200);

		// -------------------------acquire 1------------------------------
		assertEquals(12L, functions.nextSwapchain);
		var image1 = swapchains.acquire(VK_PRESENT_MODE_FIFO_KHR, false);
		assertEquals(13L, functions.nextSwapchain);
		assertEquals(12L, image1.swapchain.vkSwapchain);
		assertEquals(1, image1.index);
		assertNull(image1.acquireSubmission);
		assertThrows(UnsupportedOperationException.class, image1::getAcquireSubmission);
		assertEquals(1L, image1.getAcquireSemaphore());
		assertEquals(600, image1.getWidth());
		assertEquals(200, image1.getHeight());
		assertEquals(createSet(1L, 2L), functions.borrowedSemaphores);
		assertEquals(VK_PRESENT_MODE_FIFO_KHR, image1.presentMode);
		assertNull(image1.presentFence);
		assertEquals(2L, image1.presentSemaphore);
		// -------------------------acquire 1------------------------------

		// -------------------------present 1------------------------------
		functions.expectedSwitchPresentMode = false;
		functions.nextPresentResult = VK_SUCCESS;
		image1.swapchain.presentImage(image1);
		// -------------------------present 1------------------------------

		// Change swapchain extent to trigger a recreation
		functions.capabilities.currentExtent().set(606, 202);
		functions.expectedSwapchain = 13L;
		functions.expectedAcquireSemaphore = 3L;
		functions.expectedAcquireFence = null;
		functions.nextImageIndex = 2;
		functions.nextAcquireResult = VK_SUCCESS;

		// -------------------------acquire 2------------------------------
		assertEquals(0, functions.deviceWaitIdleCount);
		assertEquals(13L, functions.nextSwapchain);
		var image2 = swapchains.acquire(VK_PRESENT_MODE_FIFO_KHR, false);
		assertEquals(1, functions.deviceWaitIdleCount);
		assertEquals(14L, functions.nextSwapchain);
		assertEquals(13L, image2.swapchain.vkSwapchain);
		assertEquals(2, image2.index);
		assertNull(image2.acquireSubmission);
		assertThrows(UnsupportedOperationException.class, image2::getAcquireSubmission);
		assertEquals(3L, image2.getAcquireSemaphore());
		assertEquals(606, image2.getWidth());
		assertEquals(202, image2.getHeight());

		// Note that semaphore 2 (the first present semaphore) can be returned because we did vkDeviceWaitIdle
		assertEquals(createSet(1L, 3L, 4L), functions.borrowedSemaphores);
		assertEquals(VK_PRESENT_MODE_FIFO_KHR, image2.presentMode);
		assertNull(image2.presentFence);
		assertEquals(4L, image2.presentSemaphore);
		// -------------------------acquire 2------------------------------

		// -------------------------present 2------------------------------
		functions.expectedSwitchPresentMode = false;
		functions.nextPresentResult = VK_SUCCESS;
		image2.swapchain.presentImage(image2);
		// -------------------------present 2------------------------------

		swapchains.destroy();
		assertEquals(createList(12L, 13L), functions.destroyedSwapchains);
		assertEquals(createSet(), functions.borrowedSemaphores);
		assertEquals(0, functions.nextFence);
	}

	@Test
	public void testWithoutMaintenanceAndWith1() {
		var presentModes = new PresentModes(createSet(VK_PRESENT_MODE_FIFO_KHR), createSet(VK_PRESENT_MODE_FIFO_KHR));
		var properties = new WindowProperties(
				1234L, "TestTitle", 12345L, 0, VK_FORMAT_R8G8B8A8_UNORM,
				VK_COLOR_SPACE_SRGB_NONLINEAR_KHR, VK_IMAGE_USAGE_TRANSFER_DST_BIT,
				VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR, false, 1, 2
		);

		var functions = new DummySwapchainFunctions();
		functions.capabilities = VkSurfaceCapabilitiesKHR.create();
		functions.properties = properties;
		functions.numSwapchainImages = 3;

		var swapchains = new SwapchainManager(functions, properties, presentModes);

		functions.expectedSwapchain = 12L;
		functions.expectedAcquireSemaphore = VK_NULL_HANDLE;
		functions.expectedAcquireFence = functions.availableFences[0];
		functions.nextImageIndex = 2;
		functions.nextAcquireResult = VK_SUBOPTIMAL_KHR;

		functions.capabilities.currentExtent().set(600, 200);

		// -------------------------acquire 1------------------------------
		assertEquals(12L, functions.nextSwapchain);
		var image1 = swapchains.acquire(VK_PRESENT_MODE_FIFO_KHR, true);
		assertEquals(13L, functions.nextSwapchain);
		assertEquals(12L, image1.swapchain.vkSwapchain);
		assertEquals(2, image1.index);
		assertNotNull(image1.getAcquireSubmission());
		assertThrows(UnsupportedOperationException.class, image1::getAcquireSemaphore);
		assertEquals(600, image1.getWidth());
		assertEquals(200, image1.getHeight());
		assertEquals(createSet(1L), functions.borrowedSemaphores);
		assertEquals(VK_PRESENT_MODE_FIFO_KHR, image1.presentMode);
		assertNull(image1.presentFence);
		assertEquals(1L, image1.presentSemaphore);
		// -------------------------acquire 1------------------------------

		// TODO Finish the test
	}

	// TODO Test that oldSwapchain is used for recreation
}
