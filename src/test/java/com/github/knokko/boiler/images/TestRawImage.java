package com.github.knokko.boiler.images;

import com.github.knokko.boiler.builders.BoilerBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.VK_API_VERSION_1_2;

public class TestRawImage {

	@Test
	public void testRawImage() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_2, "TestRawImage", 1
		).validation().forbidValidationErrors().build();

		var image = new ImageBuilder(
				"RawImage", 12, 34
		).texture().createRaw(instance);
		assertNotEquals(VK_NULL_HANDLE, image.vkImage);
		assertEquals(VK_NULL_HANDLE, image.vkImageView);
		assertEquals(12, image.width);
		assertEquals(34, image.height);
		assertEquals(VK_IMAGE_ASPECT_COLOR_BIT, image.aspectMask);

		vkDestroyImage(instance.vkDevice(), image.vkImage, null);
		instance.destroyInitialObjects();
	}
}
