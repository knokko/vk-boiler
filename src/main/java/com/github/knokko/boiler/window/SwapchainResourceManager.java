package com.github.knokko.boiler.window;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * <i>SwapchainResourceManager</i>s can be used to <i>associate</i> (Vulkan) objects with swapchain images, such that
 * they will be created when the swapchain image is first acquired, and destroyed after the swapchain is destroyed.
 * @param <S> The type of resource to associate with swapchains
 * @param <I> The type of resource to associate with swapchain images
 */
public abstract class SwapchainResourceManager<S, I> {

	private SwapchainWrapper currentSwapchain;
	private S currentSwapchainResource;
	private List<I> currentImageResources;

	private final Queue<S> recycledSwapchains = new ConcurrentLinkedQueue<>();

	protected S createSwapchain(int width, int height, int numImages) {
		return null;
	}

	protected I createImage(S swapchain, AcquiredImage swapchainImage) {
		return null;
	}

	protected void destroySwapchain(S swapchain) {}

	protected void destroyImage(I resource) {}

	protected S recreateSwapchain(S oldSwapchain, int newWidth, int newHeight, int newNumImages) {
		destroySwapchain(oldSwapchain);
		return createSwapchain(newWidth, newHeight, newNumImages);
	}

	public I get(AcquiredImage swapchainImage) {
		if (currentImageResources == null || currentSwapchain != swapchainImage.swapchain) {
			currentImageResources = new ArrayList<>(swapchainImage.swapchain.getNumImages());
			for (int counter = 0; counter < swapchainImage.swapchain.getNumImages(); counter++) {
				currentImageResources.add(null);
			}
			currentSwapchain = swapchainImage.swapchain;
			var oldSwapchainResource = recycledSwapchains.poll();
			if (oldSwapchainResource == null) {
				currentSwapchainResource = createSwapchain(
						swapchainImage.getWidth(), swapchainImage.getHeight(), currentImageResources.size()
				);
			} else {
				currentSwapchainResource = recreateSwapchain(
						oldSwapchainResource, swapchainImage.getWidth(), swapchainImage.getHeight(), currentImageResources.size()
				);
			}

			var rememberSwapchain = currentSwapchainResource;
			if (rememberSwapchain != null) {
				swapchainImage.swapchain.destructionCallbacks.add(() -> recycledSwapchains.add(rememberSwapchain));
			}
			swapchainImage.swapchain.associations.add(this);
		}

		var currentResource = currentImageResources.get(swapchainImage.index);
		if (currentResource == null) {
			currentResource = createImage(currentSwapchainResource, swapchainImage);
			currentImageResources.set(swapchainImage.index, currentResource);

			var rememberResource = currentResource;
			if (rememberResource != null) {
				swapchainImage.swapchain.destructionCallbacks.add(() -> destroyImage(rememberResource));
			}
		}
		return currentResource;
	}

	void destroy() {
		for (S swapchain : recycledSwapchains) destroySwapchain(swapchain);
	}
}
