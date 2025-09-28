package com.github.knokko.boiler.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DummyAssociations extends SwapchainResourceManager<DummyAssociations.Swapchain, DummyAssociations.Image> {

	@Override
	public Swapchain createSwapchain(int width, int height, int numImages) {
		return new Swapchain(width, height, numImages);
	}

	@Override
	public Image createImage(Swapchain swapchain, AcquiredImage acquiredImage) {
		swapchain.createdImages += 1;
		assertEquals(swapchain.width, acquiredImage.getWidth());
		assertEquals(swapchain.height, acquiredImage.getHeight());
		return new Image(swapchain);
	}

	@Override
	public void destroySwapchain(Swapchain swapchain) {
		assertFalse(swapchain.wasDestroyed);
		swapchain.wasDestroyed = true;
	}

	@Override
	public void destroyImage(Image image) {
		assertFalse(image.wasDestroyed);
		image.wasDestroyed = true;
	}

	@Override
	public Swapchain recreateSwapchain(Swapchain old, int width, int height, int numImages) {
		old.wasDestroyed = true;
		old.wasRecycled = true;
		return createSwapchain(width, height, numImages);
	}

	static class Swapchain {

		final int width, height, numImages;

		int createdImages;
		boolean wasDestroyed, wasRecycled;

		Swapchain(int width, int height, int numImages) {
			this.width = width;
			this.height = height;
			this.numImages = numImages;
		}
	}

	static class Image {

		final Swapchain swapchain;

		boolean wasDestroyed;

		Image(Swapchain swapchain) {
			this.swapchain = swapchain;
		}
	}
}
