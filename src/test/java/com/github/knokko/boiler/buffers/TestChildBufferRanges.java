package com.github.knokko.boiler.buffers;

import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.memory.MemoryBlockBuilder;
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

		var builder = new MemoryBlockBuilder(instance, "Memory");
		var hostBuffer = builder.addMappedBuffer(100, 1, VK_BUFFER_USAGE_INDEX_BUFFER_BIT);
		var deviceBuffer = builder.addMappedBuffer(100, 1, VK_BUFFER_USAGE_INDEX_BUFFER_BIT);
		var memory = builder.allocate(false);

		{
			var parentRange = hostBuffer.child(10L, 50L);
			var childRange = parentRange.child(25L, 20L);
			assertEquals(new VkbBuffer(hostBuffer.vkBuffer, hostBuffer.offset + 35, 20), childRange);
		}

		{
			var parentRange = deviceBuffer.child(10L, 50L);
			var childRange = parentRange.child(25L, 20L);
			assertEquals(new VkbBuffer(deviceBuffer.vkBuffer, deviceBuffer.offset + 35L, 20L), childRange);
		}

		memory.free(instance);
		instance.destroyInitialObjects();
	}
}
