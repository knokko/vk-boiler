package com.github.knokko.boiler.window;

import com.github.knokko.boiler.synchronization.VkbFence;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkSwapchainPresentFenceInfoEXT;

import java.util.List;

class SwapchainMaintenanceCleaner extends SwapchainCleaner {

	@Override
	boolean chooseRemainingImages(State state, List<AcquiredImage> remainingImages) {
		boolean canDestroyOldSwapchains = false;

		for (int myImageIndex = state.acquiredImages().size() - 1; myImageIndex >= 0; myImageIndex--) {
			var image = state.acquiredImages().get(myImageIndex);
			if (image.presentFence.isSignaled()) {
				canDestroyOldSwapchains = true;
				destroyImageNow(image, true);
			} else {
				remainingImages.add(image);
			}
		}

		return canDestroyOldSwapchains;
	}

	@Override
	public VkbFence getPresentFence(String name) {
		return instance.sync.fenceBank.borrowFence(false, "PresentFence-" + name);
	}

	@Override
	public void beforePresent(MemoryStack stack, VkPresentInfoKHR presentInfo, AcquiredImage acquiredImage) {
		var fiPresent = VkSwapchainPresentFenceInfoEXT.calloc(stack);
		fiPresent.sType$Default();
		fiPresent.pFences(stack.longs(acquiredImage.presentFence.getVkFenceAndSubmit()));

		presentInfo.pNext(fiPresent);
	}

	@Override
	void waitUntilStateCanBeDestroyed(State state) {
		for (var image : state.acquiredImages()) image.presentFence.awaitSignal();
	}
}
