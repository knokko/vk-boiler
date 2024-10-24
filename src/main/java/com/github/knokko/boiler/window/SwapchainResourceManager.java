package com.github.knokko.boiler.window;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * <i>SwapchainResourceManager</i>s can be used to <i>associate</i> (Vulkan) objects with swapchain images, such that
 * they will be created when the swapchain image is first acquired, and destroyed when the swapchain is destroyed.
 * @param <T> The type of resource to associate with swapchain images
 */
public class SwapchainResourceManager<T> {

	private final Function<AcquiredImage, T> createResources;
	private final Consumer<T> destroyResources;
	private VkbSwapchain currentSwapchain;
	private List<T> currentResources;

	public SwapchainResourceManager(Function<AcquiredImage, T> createResources, Consumer<T> destroyResources) {
		this.createResources = createResources;
		this.destroyResources = destroyResources;
	}

	public T get(AcquiredImage swapchainImage) {
		if (currentResources == null || currentSwapchain != swapchainImage.swapchain) {
			currentResources = new ArrayList<>(swapchainImage.swapchain.images.length);
			for (int counter = 0; counter < swapchainImage.swapchain.images.length; counter++) {
				currentResources.add(null);
			}
			currentSwapchain = swapchainImage.swapchain;
		}

		var currentResource = currentResources.get(swapchainImage.index());
		if (currentResource == null) {
			currentResource = createResources.apply(swapchainImage);
			currentResources.set(swapchainImage.index(), currentResource);

			var rememberResource = currentResource;
			swapchainImage.swapchain.destructionCallbacks.add(() -> destroyResources.accept(rememberResource));
		}
		return currentResource;
	}
}
