package com.github.knokko.boiler.window;

import com.github.knokko.boiler.synchronization.VkbFence;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPresentInfoKHR;

import java.util.List;

class LegacySwapchainCleaner extends SwapchainCleaner {

	@Override
	boolean chooseRemainingImages(State state, List<AcquiredImage> remainingImages) {
		var canDestroyOlderImages = new boolean[state.swapchain().images.length];
		boolean canDestroyOldSwapchains = false;

		for (int myImageIndex = state.acquiredImages().size() - 1; myImageIndex >= 0; myImageIndex--) {
			var image = state.acquiredImages().get(myImageIndex);
			if (canDestroyOlderImages[image.index()]) {
				if (!image.acquireFence.isSignaled()) {
					System.err.println("How dafuq is acquire fence not signaled?");
					if (image.acquireFence.isPending()) {
						System.err.println("How is it pending?");
					}
					continue;
				}

				destroyImageNow(image, true);
			} else {
				remainingImages.add(image);
				if (image.acquireFence.isSignaled()) {
					canDestroyOldSwapchains = true;
					canDestroyOlderImages[image.index()] = true;
				}
			}
		}

		return canDestroyOldSwapchains;
	}

	@Override
	public VkbFence getPresentFence(String name) {
		return null;
	}

	@Override
	void beforePresent(MemoryStack stack, VkPresentInfoKHR presentInfo, AcquiredImage acquiredImage) {
	}

	@Override
	void waitUntilStateCanBeDestroyed(State state) {
	}
}
