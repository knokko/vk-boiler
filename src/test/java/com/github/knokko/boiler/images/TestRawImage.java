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

		var image = instance.images.createRaw(
				12, 34, VK_FORMAT_R8G8B8A8_UNORM, VK_IMAGE_USAGE_TRANSFER_SRC_BIT,
				VK_SAMPLE_COUNT_1_BIT, 1, 1, "RawImage"
		);
		assertNotEquals(VK_NULL_HANDLE, image.vkImage());
		assertEquals(VK_NULL_HANDLE, image.vkImageView());
		assertEquals(VK_NULL_HANDLE, image.vmaAllocation());
		assertEquals(12, image.width());
		assertEquals(34, image.height());

		image.destroy(instance);

		instance.destroyInitialObjects();
	}
}
