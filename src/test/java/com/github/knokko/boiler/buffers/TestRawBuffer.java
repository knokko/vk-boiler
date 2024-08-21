package com.github.knokko.boiler.buffers;

import com.github.knokko.boiler.builders.BoilerBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.lwjgl.vulkan.VK10.*;

public class TestRawBuffer {

	@Test
	public void testRawBuffer() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_0, "TestRawBuffer", 1
		).validation().forbidValidationErrors().build();

		var buffer = instance.buffers.createRaw(1234, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, "TestRaw");
		assertNotEquals(VK_NULL_HANDLE, buffer.vkBuffer());
		assertEquals(VK_NULL_HANDLE, buffer.vmaAllocation());
		assertEquals(1234, buffer.size());

		buffer.destroy(instance);

		instance.destroyInitialObjects();
	}
}
