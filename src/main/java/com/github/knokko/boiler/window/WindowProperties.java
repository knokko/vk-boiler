package com.github.knokko.boiler.window;

/**
 * Simple immutable window & swapchain properties. This record makes it easy to pass them around.
 *
 * @param handle The GLFW window handle or the SDL window handle
 * @param title The (debug) name of the window, which is shown in the title bar for decorated windows
 * @param vkSurface The <i>VkSurfaceKHR</i> of this window
 * @param numHiddenFrames The number of frames that will be 'presented' before the window becomes visible.
 *                        This can be useful since the window will display 'garbage' content until the first frame is
 *                        fully completed.
 * @param surfaceFormat The <i>VkFormat</i> of the swapchain images of the <i>VkSurfaceKHR</i> of this window
 * @param surfaceColorSpace The <i>VkColorSpaceKHR</i> of the swapchain images of the <i>VkSurfaceKHR</i> of this window
 * @param swapchainImageUsage The image usage flags for the swapchain images that will be created for this window
 * @param swapchainCompositeAlpha The composite alpha for the swapchain images that will be created for this window
 * @param usesSwapchainMaintenance Whether the swapchain of this window will
 *                                 use the <i>VK_EXT_swapchain_maintenance1</i> extension.
 * @param maxOldSwapchains The maximum number of old swapchains that the swapchain manager will allow to be piled up.
 *                         When the limit is reached, the old swapchains will be destroyed after a call to
 *                         <i>vkDeviceWaitIdle</i>, which is potentially bad for smooth resizing, but keeps memory
 *                         usage under control.
 * @param maxFramesInFlight The maximum number of frames-in-flight that the swapchain manager can handle. Using more
 *                          will result into potentially invalid synchronization.
 * @param acquireTimeout The timeout (in nanoseconds) that will be supplied to <i>VK_ACQUIRE_NEXT_IMAGE_KHR</i>
 */
public record WindowProperties(
		long handle,
		String title,
		long vkSurface,
		int numHiddenFrames,
		int surfaceFormat,
		int surfaceColorSpace,
		int swapchainImageUsage,
		int swapchainCompositeAlpha,
		boolean usesSwapchainMaintenance,
		int maxOldSwapchains,
		int maxFramesInFlight,
		long acquireTimeout
) {
}
