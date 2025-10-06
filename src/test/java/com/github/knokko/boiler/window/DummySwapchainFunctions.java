package com.github.knokko.boiler.window;

import com.github.knokko.boiler.images.VkbImage;
import com.github.knokko.boiler.synchronization.VkbFence;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;

import java.nio.IntBuffer;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DummySwapchainFunctions implements SwapchainFunctions {

	private long nextSemaphore = 1L;
	Set<Long> borrowedSemaphores = new HashSet<>();
	VkSurfaceCapabilitiesKHR capabilities;

	@Override
	public void deviceWaitIdle() {

	}

	@Override
	public void getSurfaceCapabilities(VkSurfaceCapabilitiesKHR capabilities) {
		if (this.capabilities != null) capabilities.set(this.capabilities);
	}

	@Override
	public SwapchainWrapper createSwapchain(PresentModes presentModes, int presentMode, Set<SwapchainResourceManager<?, ?>> associations, int width, int height, AcquireSemaphores acquireSemaphores, long oldSwapchain, VkSurfaceCapabilitiesKHR surfaceCapabilities, String debugName) {
		return null;
	}

	@Override
	public VkbImage[] getSwapchainImages(long vkSwapchain, int width, int height, int imageUsage, String debugName) {
		return new VkbImage[0];
	}

	@Override
	public int acquireImage(long vkSwapchain, MemoryStack stack, IntBuffer pImageIndex, VkbFence acquireFence, long acquireSemaphore) {
		return 0;
	}

	@Override
	public int presentImage(AcquiredImage2 image, boolean switchPresentMode) {
		return 0;
	}

	@Override
	public boolean hasSwapchainMaintenance() {
		return false;
	}

	@Override
	public VkbFence borrowFence(boolean startSignaled, String debugName) {
		return null;
	}

	@Override
	public void returnFence(VkbFence fence) {

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

	}
}
