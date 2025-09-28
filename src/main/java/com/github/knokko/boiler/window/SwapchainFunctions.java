package com.github.knokko.boiler.window;

import com.github.knokko.boiler.images.VkbImage;
import com.github.knokko.boiler.synchronization.VkbFence;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;

import java.nio.IntBuffer;
import java.util.Set;

interface SwapchainFunctions {

	void deviceWaitIdle();

	void getSurfaceCapabilities(VkSurfaceCapabilitiesKHR capabilities);

	SwapchainWrapper createSwapchain(
			PresentModes presentModes, int presentMode, Set<SwapchainResourceManager<?, ?>> associations,
			int width, int height, AcquireSemaphores acquireSemaphores, long oldSwapchain,
			VkSurfaceCapabilitiesKHR surfaceCapabilities, String debugName
	);

	VkbImage[] getSwapchainImages(long vkSwapchain, int width, int height, int imageUsage, String debugName);

	int acquireImage(
			long vkSwapchain, MemoryStack stack, IntBuffer pImageIndex,
			VkbFence acquireFence, long acquireSemaphore
	);

	int presentImage(AcquiredImage image, boolean switchPresentMode);

	boolean hasSwapchainMaintenance();

	VkbFence borrowFence(boolean startSignaled, String debugName);

	void returnFence(VkbFence fence);

	long borrowSemaphore(String debugName);

	void returnSemaphore(long vkSemaphore);

	void destroySwapchain(long vkSwapchain, VkbImage[] images);
}
