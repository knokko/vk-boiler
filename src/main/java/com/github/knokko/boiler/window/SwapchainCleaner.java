package com.github.knokko.boiler.window;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.synchronization.VkbFence;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPresentInfoKHR;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.vkDeviceWaitIdle;

abstract class SwapchainCleaner {

	BoilerInstance instance;
	final List<State> swapchains = new ArrayList<>();

	void onAcquire(AcquiredImage acquiredImage) {
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

			List<AcquiredImage> remainingImages = new ArrayList<>(state.acquiredImages.size());

			boolean canDestroyOldSwapchains = chooseRemainingImages(state, remainingImages);

			state.acquiredImages.clear();
			Collections.reverse(remainingImages);
			state.acquiredImages.addAll(remainingImages);

			if (canDestroyOldSwapchains && swapchainIndex > 0) {
				int oldSwapchainIndex = swapchainIndex;
				while (swapchainIndex > 0) {
					swapchainIndex -= 1;
					destroyStateNow(swapchains.get(swapchainIndex), true);
				}
				var newSwapchains = new ArrayList<>(swapchains.subList(oldSwapchainIndex, swapchains.size()));
				swapchains.clear();
				swapchains.addAll(newSwapchains);
			}
		}
	}

	abstract boolean chooseRemainingImages(State state, List<AcquiredImage> remainingImages);

	private void destroyStateNow(State state, boolean doSafetyChecks) {
		for (var image : state.acquiredImages) destroyImageNow(image, doSafetyChecks);
		state.swapchain.destroyNow();
	}

	void destroyImageNow(AcquiredImage image, boolean doSafetyChecks) {
		if (doSafetyChecks) {
			if (image.acquireFence != null && !image.acquireFence.isSignaled()) {
				long startTime = System.nanoTime();
				image.acquireFence.awaitSignal();
				long endTime = System.nanoTime();
				System.out.println("Weird: had to wait " + (endTime - startTime) + "ns for acquire fence...");
			}

			if (image.renderSubmission == null) {
				System.out.println("VkBoiler.SwapchainCleaner: it looks like a acquired image was never presented: " +
						"falling back to vkDeviceWaitIdle...");
				assertVkSuccess(vkDeviceWaitIdle(
						instance.vkDevice()
				), "DeviceWaitIdle", "SwapchainCleaner.destroyImageNow");
			} else {
				image.renderSubmission.awaitCompletion();
			}
		}

		if (image.acquireFence != null) instance.sync.fenceBank.returnFence(image.acquireFence);
		if (image.presentFence != null) instance.sync.fenceBank.returnFence(image.presentFence);
		if (image.acquireSemaphore != VK_NULL_HANDLE) {
			instance.sync.semaphoreBank.returnSemaphores(image.acquireSemaphore);
		}
		instance.sync.semaphoreBank.returnSemaphores(image.presentSemaphore());
	}

	// Don't allow destroyed swapchains to pile up!
	boolean shouldPostponeSwapchainRecreation() {
		destroyOldResources();
		return swapchains.size() > 1;
	}

	public long getLatestSwapchainHandle() {
		if (swapchains.isEmpty()) return VK_NULL_HANDLE;
		return swapchains.get(swapchains.size() - 1).swapchain.vkSwapchain;
	}

	public void onChangeCurrentSwapchain(VkbSwapchain oldSwapchain, VkbSwapchain newSwapchain) {
		if (!swapchains.isEmpty() && swapchains.get(swapchains.size() - 1).swapchain != oldSwapchain && oldSwapchain != null) {
			throw new IllegalStateException("Missed the switch to swapchain " + oldSwapchain);
		}
		if (newSwapchain != null) {
			if (swapchains.stream().anyMatch(state -> state.swapchain == newSwapchain)) {
				throw new IllegalStateException("Swapchain used twice? " + newSwapchain);
			}
			swapchains.add(new State(newSwapchain, new ArrayList<>()));
		}
	}

	abstract VkbFence getPresentFence(String name);

	abstract void beforePresent(MemoryStack stack, VkPresentInfoKHR presentInfo, AcquiredImage acquiredImage);

	abstract void waitUntilStateCanBeDestroyed(State state);

	void destroyEverything() {
		for (var state : swapchains) {
			for (var image : state.acquiredImages) {
				if (image.acquireFence != null) image.acquireFence.waitIfSubmitted();
			}
			waitUntilStateCanBeDestroyed(state);
			destroyStateNow(state, false);
		}
		swapchains.clear();
	}

	record State(VkbSwapchain swapchain, List<AcquiredImage> acquiredImages) {
	}
}
