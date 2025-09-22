package com.github.knokko.boiler.memory;

import com.github.knokko.boiler.buffers.VkbBuffer;
import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.builders.device.SimpleDeviceSelector;
import com.github.knokko.boiler.images.ImageBuilder;
import com.github.knokko.boiler.images.VkbImage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK13.VK_API_VERSION_1_3;

public class TestBufferImageGranularity {

	private void checkForValidationErrors(int deviceType) {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_3, "TestBufferImageGranularity", 1
		).validation().forbidValidationErrors().physicalDeviceSelector(new SimpleDeviceSelector(deviceType)).build();

		long granularity = instance.deviceProperties.limits().bufferImageGranularity();
		if (granularity != 1L) {
			var combiner = new MemoryCombiner(instance, "GranularityMemory");
			VkbBuffer buffer = combiner.addBuffer(1234L, 1L, VK_BUFFER_USAGE_INDEX_BUFFER_BIT, 1f);
			VkbImage image = combiner.addImage(new ImageBuilder("GranImage", 11, 12).texture(), 1f);
			var memory = combiner.build(false);

			assertEquals(1234L, buffer.size);
			assertEquals(11, image.width);
			assertEquals(12, image.height);

			memory.destroy(instance);
		}

		instance.destroyInitialObjects();
	}

	@Test
	public void checkForValidationErrors() {
		int[] deviceTypes = {
				VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU,
				VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU,
				VK_PHYSICAL_DEVICE_TYPE_CPU,
		};
		for (int deviceType : deviceTypes) checkForValidationErrors(deviceType);
	}

	@Test
	public void checkArithmetic() {
		var claims = new MemoryTypeClaims();

		var bufferClaims1 = new BufferUsageClaims();
		bufferClaims1.claims.add(new BufferClaim(new VkbBuffer(12L), 13L));
		bufferClaims1.claims.add(new BufferClaim(new VkbBuffer(13L), 15L));
		assertEquals(28L, bufferClaims1.computeSize());
		bufferClaims1.setBuffer(123L, 29L, 13L);

		var bufferClaims2 = new BufferUsageClaims();
		bufferClaims2.claims.add(new BufferClaim(new VkbBuffer(1029L), 27L));
		assertEquals(1029L, bufferClaims2.computeSize());
		bufferClaims2.setBuffer(124L, 1029L, 27L);

		claims.buffers.add(bufferClaims1);
		claims.buffers.add(bufferClaims2);

		var linearBuilder = new ImageBuilder("Linear", 1, 1).tiling(VK_IMAGE_TILING_LINEAR);
		var optimalBuilder = new ImageBuilder("Optimal", 1, 1);
		var linearImageClaim = new ImageClaim(null, linearBuilder, 234L, 23L);
		var optimalImageClaim = new ImageClaim(null, optimalBuilder, 123L, 47L);
		claims.images.add(optimalImageClaim);
		claims.images.add(linearImageClaim);

		long size = claims.prepareAllocations(1024L);
		assertEquals(0, bufferClaims1.memoryOffset);
		// bufferClaims1 should end at byte 29

		assertEquals(54, bufferClaims2.memoryOffset);
		// bufferClaims2 should end at 54 + 1029 = 1083

		assertEquals(1104, linearImageClaim.memoryOffset);
		// linearImageClaim should end at 1104 + 234 = 1338, which is on page 2 since 1338 > 1 * 1024

		// so the image should be placed at page 3, at offset >= 2048
		// 2068 is the smallest multiple of the alignment 47 that is at least 2048
		assertEquals(2068, optimalImageClaim.memoryOffset);
		assertEquals(2191, size);
	}
}
