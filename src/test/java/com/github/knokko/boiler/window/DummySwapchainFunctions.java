package com.github.knokko.boiler.window;

import com.github.knokko.boiler.images.VkbImage;
import com.github.knokko.boiler.synchronization.DummyFence;
import com.github.knokko.boiler.synchronization.VkbFence;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfacePresentModeCompatibilityEXT;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.system.MemoryUtil.memCopy;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;

class DummySwapchainFunctions implements SwapchainFunctions {

	VkSurfaceCapabilitiesKHR capabilities;
	VkSurfacePresentModeCompatibilityEXT presentModeCompatibility;
	WindowProperties properties;
	int deviceWaitIdleCount;

	// For borrowing semaphores
	private long nextSemaphore = 1L;
	Set<Long> borrowedSemaphores = new HashSet<>();

	// For swapchains
	long nextSwapchain = 12L;
	List<Long> destroyedSwapchains = new ArrayList<>();
	int numSwapchainImages;
	long expectedOldSwapchain;

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
			this.availableFences[index] = new DummyFence(false);
		}
	}

	@Override
	public void deviceWaitIdle() {
		this.deviceWaitIdleCount += 1;
		for (VkbFence fence : availableFences) {
			if (fence.isPending()) fence.forceSignal();
		}
	}

	@Override
	public void getSurfaceCapabilities(VkSurfaceCapabilitiesKHR capabilities) {
		memCopy(this.capabilities, capabilities);
	}

	@Override
	public SwapchainWrapper createSwapchain(
			PresentModes presentModes, int presentMode, Set<SwapchainResourceManager<?, ?>> associations,
			int width, int height, AcquireSemaphores acquireSemaphores, long oldSwapchain,
			VkSurfaceCapabilitiesKHR surfaceCapabilities, String debugName
	) {
		assertEquals(expectedOldSwapchain, oldSwapchain);
		long vkSwapchain = nextSwapchain++;

		if (properties.usesSwapchainMaintenance()) {
			try (MemoryStack stack = MemoryStack.stackPush()) {
				int numCompatiblePresentModes = presentModeCompatibility.presentModeCount();
				var compatiblePresentModeBuffer = presentModeCompatibility.pPresentModes();
				for (int index = 0; index < numCompatiblePresentModes; index++) {
					assert compatiblePresentModeBuffer != null;
					int compatiblePresentMode = compatiblePresentModeBuffer.get(index);
					presentModes.compatible.add(compatiblePresentMode);
				}
				presentModes.createSwapchain(stack, presentMode, compatiblePresentModeBuffer);
			}
		} else {
			presentModes.createSwapchain(null, presentMode, null);
		}
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
		if (nextAcquireResult >= VK_SUCCESS && acquireFence != null) acquireFence.getVkFenceAndSubmit();
		return nextAcquireResult;
	}

	@Override
	public int presentImage(AcquiredImage image, boolean switchPresentMode) {
		assertEquals(expectedSwitchPresentMode, switchPresentMode);
		if (image.presentFence != null) {
			assertFalse(image.presentFence.isPending());
			assertFalse(image.presentFence.isSignaled());
			image.presentFence.getVkFenceAndSubmit();
		}
		return nextPresentResult;
	}

	@Override
	public boolean hasSwapchainMaintenance() {
		return properties.usesSwapchainMaintenance();
	}

	@Override
	public VkbFence borrowFence(boolean startSignaled, String debugName) {
		var iterator = returnedFences.iterator();
		while (iterator.hasNext()) {
			var fence = iterator.next();
			if (!fence.isPending()) {
				if (startSignaled) fence.forceSignal();
				else fence.forceReset();
				iterator.remove();
				return fence;
			}
		}

		return availableFences[nextFence++];
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
