package com.github.knokko.boiler.commands;

import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.exceptions.VulkanFailureException;
import com.github.knokko.boiler.memory.MemoryCombiner;
import com.github.knokko.boiler.synchronization.ResourceUsage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.lwjgl.vulkan.VK10.*;

public class TestSingleTimeCommands {

	@Test
	public void testCustomTimeout() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_0, "TestCustomTimeout", 1
		).validation().forbidValidationErrors().build();

		var combiner = new MemoryCombiner(instance, "TestingMemory");
		long size = 100_000_000L;
		var sourceBuffer = combiner.addMappedBuffer(size, 4L, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
		var middleBuffer = combiner.addBuffer(
				size, 4L, VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT, 1f
		);
		var destinationBuffer = combiner.addMappedBuffer(size, 4L, VK_BUFFER_USAGE_TRANSFER_DST_BIT);
		var memory = combiner.build(false);

		var sourceBytes = sourceBuffer.byteBuffer();
		var destinationBytes = destinationBuffer.byteBuffer();
		for (int counter = 0; counter < 100; counter++) {
			sourceBytes.put((byte) counter);
		}

		var commands = SingleTimeCommands.submit(instance, "VeryShortTimeout", recorder -> {
			recorder.copyBuffer(sourceBuffer, middleBuffer);
			recorder.bufferBarrier(middleBuffer, ResourceUsage.TRANSFER_DEST, ResourceUsage.TRANSFER_SOURCE);
			recorder.copyBuffer(middleBuffer, destinationBuffer);
			recorder.bufferBarrier(destinationBuffer, ResourceUsage.TRANSFER_DEST, ResourceUsage.HOST_READ);
		});

		// No way copying 100MB twice can be done in less than 1ns
		assertThrows(VulkanFailureException.class, () -> commands.destroy(1L));

		// The default command timeout should however be more than enough
		commands.destroy();

		for (int counter = 0; counter < 100; counter++) {
			assertEquals(counter, destinationBytes.get());
		}

		memory.destroy(instance);
		instance.destroyInitialObjects();
	}
}
