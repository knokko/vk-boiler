package com.github.knokko.boiler.memory;

import com.github.knokko.boiler.buffers.VkbBuffer;
import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.commands.SingleTimeCommands;
import com.github.knokko.boiler.images.ImageBuilder;
import com.github.knokko.boiler.synchronization.ResourceUsage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.lwjgl.vulkan.VK10.*;

public class TestMemoryRecycling {

	@Test
	public void testCanNotRecycleSmallBlocks() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_0, "TestRecycleMemoryTooSmall", 1
		).validation().forbidValidationErrors().build();

		var oldCombiner = new MemoryCombiner(instance, "OldMemory");
		var oldMappedBuffer = oldCombiner.addMappedBuffer(10_000, 1, VK_BUFFER_USAGE_TRANSFER_DST_BIT);
		oldCombiner.addBuffer(10_000, 1, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
		oldCombiner.addImage(new ImageBuilder("OldImage", 10, 10).texture());
		var oldBlock = oldCombiner.build(false);

		var newCombiner = new MemoryCombiner(instance, "OldMemory");
		var newMappedBuffer = newCombiner.addMappedBuffer(40_000, 1, VK_BUFFER_USAGE_TRANSFER_DST_BIT);
		var newDeviceBuffer = newCombiner.addBuffer(55_000, 4, VK_BUFFER_USAGE_TRANSFER_DST_BIT);
		var newImage = newCombiner.addImage(new ImageBuilder("NewImage", 20, 20).texture());
		var newBlock = newCombiner.buildAndRecycle(oldBlock);

		assertNotEquals(oldMappedBuffer.hostAddress, newMappedBuffer.hostAddress);
		assertEquals(40_000, newMappedBuffer.size);
		assertEquals(55_000, newDeviceBuffer.size);

		SingleTimeCommands.submit(instance, "SimpleCopyCheck", recorder -> {
			for (var buffer : new VkbBuffer[] { newMappedBuffer, newDeviceBuffer }) {
				vkCmdFillBuffer(recorder.commandBuffer, buffer.vkBuffer, buffer.offset, buffer.size, 123456);
			}
			recorder.transitionLayout(newImage, null, ResourceUsage.TRANSFER_DEST);
			recorder.clearColorImage(newImage.vkImage, 1f, 0f, 1f, 1f);
		}).destroy();

		var intBuffer = newMappedBuffer.intBuffer();
		assertEquals(10_000, intBuffer.remaining());
		while (intBuffer.hasRemaining()) {
			assertEquals(123456, intBuffer.get());
		}

		newBlock.destroy(instance);
		instance.destroyInitialObjects();
	}

	@Test
	public void testRecycleLargerBlocks() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_0, "TestRecycleMemoryTooSmall", 1
		).validation().forbidValidationErrors().build();

		var oldCombiner = new MemoryCombiner(instance, "OldMemory");
		var oldMappedBuffer = oldCombiner.addMappedBuffer(100_000, 1, VK_BUFFER_USAGE_TRANSFER_DST_BIT);
		oldCombiner.addBuffer(100_000, 1, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
		oldCombiner.addImage(new ImageBuilder("OldImage", 50, 50).texture());
		var oldBlock = oldCombiner.build(false);

		var newCombiner = new MemoryCombiner(instance, "OldMemory");
		var newMappedBuffer = newCombiner.addMappedBuffer(40_000, 1, VK_BUFFER_USAGE_TRANSFER_DST_BIT);
		var newDeviceBuffer = newCombiner.addBuffer(55_000, 4, VK_BUFFER_USAGE_TRANSFER_DST_BIT);
		var newImage = newCombiner.addImage(new ImageBuilder("NewImage", 20, 20).texture());
		var newBlock = newCombiner.buildAndRecycle(oldBlock);

		assertEquals(oldMappedBuffer.hostAddress, newMappedBuffer.hostAddress);
		assertEquals(40_000, newMappedBuffer.size);
		assertEquals(55_000, newDeviceBuffer.size);

		SingleTimeCommands.submit(instance, "SimpleCopyCheck", recorder -> {
			for (var buffer : new VkbBuffer[] { newMappedBuffer, newDeviceBuffer }) {
				vkCmdFillBuffer(recorder.commandBuffer, buffer.vkBuffer, buffer.offset, buffer.size, 123456);
			}
			recorder.transitionLayout(newImage, null, ResourceUsage.TRANSFER_DEST);
			recorder.clearColorImage(newImage.vkImage, 1f, 0f, 1f, 1f);
		}).destroy();

		var intBuffer = newMappedBuffer.intBuffer();
		assertEquals(10_000, intBuffer.remaining());
		while (intBuffer.hasRemaining()) {
			assertEquals(123456, intBuffer.get());
		}

		newBlock.destroy(instance);
		instance.destroyInitialObjects();
	}
}
