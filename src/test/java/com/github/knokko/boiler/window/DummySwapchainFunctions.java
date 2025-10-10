package com.github.knokko.boiler.window;

import com.github.knokko.boiler.images.VkbImage;
import com.github.knokko.boiler.synchronization.DummyFence;
import com.github.knokko.boiler.synchronization.VkbFence;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.system.MemoryUtil.memCopy;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;

class DummySwapchainFunctions implements SwapchainFunctions {

	VkSurfaceCapabilitiesKHR capabilities;
	WindowProperties properties;
	int deviceWaitIdleCount;

	// For borrowing semaphores
	private long nextSemaphore = 1L;
	Set<Long> borrowedSemaphores = new HashSet<>();

	// For swapchains
	long nextSwapchain = 12L;
	List<Long> destroyedSwapchains = new ArrayList<>();
	int numSwapchainImages;

	// For borrowing fences
	VkbFence[] availableFences;
	int nextFence;
	Set<VkbFence> returnedFences = new HashSet<>();

	// For vkAcquireNextImageKHR
	long expectedSwapchain, expectedAcquireSemaphore;
	VkbFence expectedAcquireFence;
	int nextImageIndex, nextAcquireResult;

	// For vkQueuePresentKHR
	boolean expectedSwitchPresentMode;
	int nextPresentResult;

	DummySwapchainFunctions() {
		int numFences = 5;
		this.availableFences = new VkbFence[numFences];
		for (int index = 0; index < numFences; index++) {
			this.availableFences[index] = DummyFence.create(false);
		}
	}

	@Override
	public void deviceWaitIdle() {
		this.deviceWaitIdleCount += 1;
	}

	@Override
	public void getSurfaceCapabilities(VkSurfaceCapabilitiesKHR capabilities) {
		memCopy(this.capabilities, capabilities);
	}

	@Override
	public SwapchainWrapper createSwapchain(PresentModes presentModes, int presentMode, Set<SwapchainResourceManager<?, ?>> associations, int width, int height, AcquireSemaphores acquireSemaphores, long oldSwapchain, VkSurfaceCapabilitiesKHR surfaceCapabilities, String debugName) {
		long vkSwapchain = nextSwapchain++;
		return new SwapchainWrapper(
				vkSwapchain, this, properties, presentModes,
				associations, width, height, acquireSemaphores, debugName
		);
	}

	@Override
	public VkbImage[] getSwapchainImages(long vkSwapchain, int width, int height, int imageUsage, String debugName) {
		VkbImage[] images = new VkbImage[numSwapchainImages];
		for (int index = 0; index < numSwapchainImages; index++) {
			images[index] = new VkbImage(vkSwapchain + 1000 + index, width, height, VK_IMAGE_ASPECT_COLOR_BIT);
		}
		return images;
	}

	@Override
	public int acquireImage(long vkSwapchain, MemoryStack stack, IntBuffer pImageIndex, VkbFence acquireFence, long acquireSemaphore) {
		assertEquals(expectedSwapchain, vkSwapchain);
		assertEquals(expectedAcquireSemaphore, acquireSemaphore);
		assertSame(expectedAcquireFence, acquireFence);
		pImageIndex.put(0, nextImageIndex);
		return nextAcquireResult;
	}

	@Override
	public int presentImage(AcquiredImage image, boolean switchPresentMode) {
		assertEquals(expectedSwitchPresentMode, switchPresentMode);
		return nextPresentResult;
	}

	@Override
	public boolean hasSwapchainMaintenance() {
		return properties.usesSwapchainMaintenance();
	}

	@Override
	public VkbFence borrowFence(boolean startSignaled, String debugName) {
		if (returnedFences.isEmpty()) {
			return availableFences[nextFence++];
		} else {
			var fence = returnedFences.iterator().next();
			assertTrue(returnedFences.remove(fence));
			return fence;
		}
	}

	@Override
	public void returnFence(VkbFence fence) {
		assertTrue(returnedFences.add(fence));
	}

	@Override
	public long borrowSemaphore(String debugName) {
		long semaphore = nextSemaphore++;
		assertFalse(borrowedSemaphores.contains(semaphore));
		borrowedSemaphores.add(semaphore);
		return semaphore;
	}

	@Override
	public void returnSemaphore(long vkSemaphore) {
		assertTrue(borrowedSemaphores.contains(vkSemaphore));
		borrowedSemaphores.remove(vkSemaphore);
	}

	@Override
	public void destroySwapchain(long vkSwapchain, VkbImage[] images) {
		destroyedSwapchains.add(vkSwapchain);
	}
}
