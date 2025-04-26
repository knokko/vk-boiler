package com.github.knokko.boiler.buffers;

import com.github.knokko.boiler.builders.BoilerBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.vulkan.VK10.VK_API_VERSION_1_0;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;

public class TestChildBufferRanges {

	@Test
	public void testChildBufferRanges() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_0, "TestChildBufferRanges", 1
		).validation().forbidValidationErrors().build();
		var hostBuffer = instance.buffers.createMapped(100L, VK_BUFFER_USAGE_INDEX_BUFFER_BIT, "HostBuffer");
		var deviceBuffer = instance.buffers.create(100L, VK_BUFFER_USAGE_INDEX_BUFFER_BIT, "DeviceBuffer");

		{
			var parentRange = hostBuffer.mappedRange(10L, 50L);
			var childRange = parentRange.childRange(25L, 20L);
			assertEquals(new MappedVkbBufferRange(hostBuffer, 35, 20), childRange);
		}

		{
			var parentRange = deviceBuffer.range(10L, 50L);
			var childRange = parentRange.childRange(25L, 20L);
			assertEquals(new VkbBufferRange(deviceBuffer, 35L, 20L), childRange);
		}

		deviceBuffer.destroy(instance);
		hostBuffer.destroy(instance);
		instance.destroyInitialObjects();
	}
}
