package com.github.knokko.boiler.window;

import com.github.knokko.boiler.instance.BoilerInstance;
import com.github.knokko.boiler.sync.VkbFence;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkSwapchainPresentFenceInfoEXT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.vkDeviceWaitIdle;

class SwapchainMaintenanceCleaner implements SwapchainCleaner {

	private final BoilerInstance instance;
	private final List<State> swapchains = new ArrayList<>();

	SwapchainMaintenanceCleaner(BoilerInstance instance) {
		this.instance = instance;
	}

	@Override
	public void onAcquire(AcquiredImage acquiredImage) {
		// TODO Perhaps share code with LegacySwapchainCleaner?
		if (swapchains.isEmpty()) {
			throw new IllegalStateException("Unexpected image acquire: call onChangeCurrentSwapchain first");
		}
		var lastState = swapchains.get(swapchains.size() - 1);
		if (acquiredImage.swapchain != lastState.swapchain) {
			throw new IllegalStateException("Unexpected swapchain at image acquire");
		}

		lastState.acquiredImages.add(acquiredImage);

		destroyOldResources();
	}

	private void destroyOldResources() {
		for (int swapchainIndex = swapchains.size() - 1; swapchainIndex >= 0; swapchainIndex--) {
			var state = swapchains.get(swapchainIndex);

			boolean canDestroyOldSwapchains = false;

			List<AcquiredImage> remainingImages = new ArrayList<>(state.acquiredImages.size());
			for (int myImageIndex = state.acquiredImages.size() - 1; myImageIndex >= 0; myImageIndex--) {
				var image = state.acquiredImages.get(myImageIndex);
				if (image.presentFence.isSignaled()) {
					canDestroyOldSwapchains = true;
					destroyImageNow(image, true);
				} else {
					remainingImages.add(image);
				}
			}

			state.acquiredImages.clear();
			Collections.reverse(remainingImages);
			state.acquiredImages.addAll(remainingImages);

			if (canDestroyOldSwapchains && swapchainIndex > 0) {
				System.out.println("Destroying " + swapchainIndex + " of the " + swapchains.size() + " swapchains");
				while (swapchainIndex > 0) {
					swapchainIndex -= 1;
					destroyStateNow(swapchains.get(swapchainIndex), true);
				}
				swapchains.clear();
				swapchains.add(state);
			}
		}
	}

	private void destroyImageNow(AcquiredImage image, boolean doSafetyChecks) {
		if (doSafetyChecks) {
			if (!image.acquireFence.isSignaled()) {
				throw new IllegalStateException("Acquire fence should be signaled by now!");
			}

			if (image.renderSubmission == null) {
				System.out.println("VkBoiler.SwapchainMaintenanceCleaner: it looks like a acquired image was never presented: " +
						"falling back to vkDeviceWaitIdle...");
				assertVkSuccess(vkDeviceWaitIdle(
						instance.vkDevice()
				), "DeviceWaitIdle", "SwapchainMaintenanceCleaner.destroyImageNow");
			} else {
				image.renderSubmission.awaitCompletion();
			}
		}

		instance.sync.fenceBank.returnFences(image.acquireFence, image.presentFence);
		if (image.acquireSemaphore != VK_NULL_HANDLE) {
			instance.sync.semaphoreBank.returnSemaphores(image.acquireSemaphore);
		}
		instance.sync.semaphoreBank.returnSemaphores(image.presentSemaphore());
	}

	private void destroyStateNow(State state, boolean doSafetyChecks) {
		for (var image : state.acquiredImages) destroyImageNow(image, doSafetyChecks);
		state.swapchain.destroyNow();
	}

	@Override
	public void onChangeCurrentSwapchain(VkbSwapchain oldSwapchain, VkbSwapchain newSwapchain) {
		if (!swapchains.isEmpty() && swapchains.get(swapchains.size() - 1).swapchain != oldSwapchain) {
			throw new IllegalStateException("Missed the switch to swapchain " + oldSwapchain);
		}
		if (newSwapchain != null) {
			if (swapchains.stream().anyMatch(state -> state.swapchain == newSwapchain)) {
				throw new IllegalStateException("Swapchain used twice? " + newSwapchain);
			}

			// Prevent swapchains from piling up when they are destroyed too quickly
			if (swapchains.size() > 5) destroyNow();

			swapchains.add(new State(newSwapchain, new ArrayList<>()));
		}
	}

	@Override
	public VkbFence getPresentFence() {
		return instance.sync.fenceBank.borrowFence(false, "PresentFence");
	}

	@Override
	public void beforePresent(MemoryStack stack, VkPresentInfoKHR presentInfo, AcquiredImage acquiredImage) {
		var fiPresent = VkSwapchainPresentFenceInfoEXT.calloc(stack);
		fiPresent.sType$Default();
		fiPresent.pFences(stack.longs(acquiredImage.presentFence.getVkFenceAndSubmit()));

		presentInfo.pNext(fiPresent);
	}

	@Override
	public void destroyNow() {
		assertVkSuccess(vkDeviceWaitIdle(
				instance.vkDevice()
		), "DeviceWaitIdle", "SwapchainMaintenanceCleaner.destroyNow");
		for (var state : swapchains) {
			for (var image : state.acquiredImages) image.presentFence.awaitSignal();
			destroyStateNow(state, false);
		}
		swapchains.clear();
	}

	private record State(VkbSwapchain swapchain, List<AcquiredImage> acquiredImages) {}
}
