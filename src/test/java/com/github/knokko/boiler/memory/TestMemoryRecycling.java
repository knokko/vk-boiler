package com.github.knokko.boiler.memory;

import com.github.knokko.boiler.buffers.MappedVkbBuffer;
import com.github.knokko.boiler.buffers.VkbBuffer;
import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.commands.SingleTimeCommands;
import com.github.knokko.boiler.images.ImageBuilder;
import com.github.knokko.boiler.images.VkbImage;
import com.github.knokko.boiler.synchronization.ResourceUsage;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VkPhysicalDeviceMaintenance3PropertiesKHR;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.system.MemoryUtil.memPutLong;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.VK_API_VERSION_1_1;

public class TestMemoryRecycling {

	@Test
	public void testCanNotRecycleSmallBlocks() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_0, "TestRecycleMemoryTooSmall", 1
		).validation().forbidValidationErrors().build();

		var oldCombiner = new MemoryCombiner(instance, "OldMemory");
		var oldMappedBuffer = oldCombiner.addMappedBuffer(10_000, 1, VK_BUFFER_USAGE_TRANSFER_DST_BIT);
		oldCombiner.addBuffer(10_000, 1, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, 1f);
		oldCombiner.addImage(new ImageBuilder("OldImage", 10, 10).texture(), 1f);
		var oldBlock = oldCombiner.build(false);

		var newCombiner = new MemoryCombiner(instance, "RecycleMemory");
		var newMappedBuffer = newCombiner.addMappedBuffer(40_000, 1, VK_BUFFER_USAGE_TRANSFER_DST_BIT);
		var newDeviceBuffer = newCombiner.addBuffer(55_000, 4, VK_BUFFER_USAGE_TRANSFER_DST_BIT, 1f);
		var newImage = newCombiner.addImage(new ImageBuilder("NewImage", 20, 20).texture(), 1f);
		var newBlock = newCombiner.buildAndRecycle(oldBlock);

		assertNotEquals(oldMappedBuffer.hostAddress, newMappedBuffer.hostAddress);
		assertEquals(40_000, newMappedBuffer.size);
		assertEquals(55_000, newDeviceBuffer.size);

		SingleTimeCommands.submit(instance, "SimpleCopyCheck", recorder -> {
			for (var buffer : new VkbBuffer[] { newMappedBuffer, newDeviceBuffer }) {
				vkCmdFillBuffer(recorder.commandBuffer, buffer.vkBuffer, buffer.offset, buffer.size, 123456);
				recorder.bufferBarrier(buffer, ResourceUsage.TRANSFER_DEST, ResourceUsage.HOST_READ);
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
				VK_API_VERSION_1_0, "TestRecycleMemoryLargerBlocks", 1
		).validation().forbidValidationErrors().build();

		var oldCombiner = new MemoryCombiner(instance, "OldMemory");
		var oldMappedBuffer = oldCombiner.addMappedBuffer(100_000, 1, VK_BUFFER_USAGE_TRANSFER_DST_BIT);
		oldCombiner.addBuffer(100_000, 1, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, 0.5f);
		oldCombiner.addImage(new ImageBuilder("OldImage", 50, 50).texture(), 1f);
		var oldBlock = oldCombiner.build(false);

		var newCombiner = new MemoryCombiner(instance, "RecycleMemory");
		var newMappedBuffer = newCombiner.addMappedBuffer(40_000, 1, VK_BUFFER_USAGE_TRANSFER_DST_BIT);
		var newDeviceBuffer = newCombiner.addBuffer(55_000, 4, VK_BUFFER_USAGE_TRANSFER_DST_BIT, 1f);
		var newImage = newCombiner.addImage(new ImageBuilder("NewImage", 20, 20).texture(), 0.5f);
		var newBlock = newCombiner.buildAndRecycle(oldBlock);

		// Note that there are multiply valid results when both buffers are device-local.
		// This typically happens on CPU implementations of Vulkan (e.g. on GitHub Actions).
		if (!newMappedBuffer.isDeviceLocal(instance)) {
			assertEquals(oldMappedBuffer.hostAddress, newMappedBuffer.hostAddress);
		}

		assertEquals(40_000, newMappedBuffer.size);
		assertEquals(55_000, newDeviceBuffer.size);

		SingleTimeCommands.submit(instance, "SimpleCopyCheck", recorder -> {
			for (var buffer : new VkbBuffer[] { newMappedBuffer, newDeviceBuffer }) {
				vkCmdFillBuffer(recorder.commandBuffer, buffer.vkBuffer, buffer.offset, buffer.size, 123456);
				recorder.bufferBarrier(buffer, ResourceUsage.TRANSFER_DEST, ResourceUsage.HOST_READ);
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
	public void testRecycleWithSmallMaximumAllocationSize() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_1, "TestRecycleMemoryWithSmallMaxAllocationSize", 1
		).validation().forbidValidationErrors().build();

		assertNotNull(instance.maintenance3Properties);
		memPutLong(instance.maintenance3Properties.address() + VkPhysicalDeviceMaintenance3PropertiesKHR.MAXMEMORYALLOCATIONSIZE, 30_000L);

		var oldCombiner = new MemoryCombiner(instance, "OldMemory");
		var oldMappedBuffer = oldCombiner.addMappedBuffer(30_000, 1, VK_BUFFER_USAGE_TRANSFER_DST_BIT);
		oldCombiner.addBuffer(25_000, 1, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, 0.5f);
		oldCombiner.addImage(new ImageBuilder("OldImage", 50, 50).texture(), 1f);
		var oldBlock = oldCombiner.build(false);

		var newCombiner = new MemoryCombiner(instance, "OldMemory");
		var newMappedBuffers = new MappedVkbBuffer[10];
		var newDeviceBuffers = new VkbBuffer[newMappedBuffers.length];
		var newImages = new VkbImage[newMappedBuffers.length];
		for (int index = 0; index < newMappedBuffers.length; index++) {
			newMappedBuffers[index] = newCombiner.addMappedBuffer(10_000, 1, VK_BUFFER_USAGE_TRANSFER_DST_BIT);
			newDeviceBuffers[index] = newCombiner.addBuffer(10_000, 4, VK_BUFFER_USAGE_TRANSFER_DST_BIT, 1f);
			newImages[index] = newCombiner.addImage(new ImageBuilder("NewImage", 26, 26).texture(), 0.5f);
		}

		var newBlock = newCombiner.buildAndRecycle(oldBlock);

		// Note that there are multiply valid results when both buffers are device-local.
		// This typically happens on CPU implementations of Vulkan (e.g. on GitHub Actions).
		if (!newMappedBuffers[0].isDeviceLocal(instance)) {

			// The first 3 buffers should go into allocation 0, and share the host address
			for (int index = 0; index < 3; index++) {
				assertEquals(oldMappedBuffer.hostAddress + 10_000 * index, newMappedBuffers[index].hostAddress);
			}
			for (int index = 3; index < newMappedBuffers.length; index++) {
				assertNotEquals(oldMappedBuffer.hostAddress, newMappedBuffers[index].hostAddress);
			}
		}

		for (int index = 0; index < 10; index++) {
			assertEquals(10_000, newMappedBuffers[index].size);
			assertEquals(10_000, newDeviceBuffers[index].size);
		}

		SingleTimeCommands.submit(instance, "SimpleCopyCheck", recorder -> {
			for (int index = 0; index < newMappedBuffers.length; index++) {
				for (var buffer : new VkbBuffer[] { newMappedBuffers[index], newDeviceBuffers[index] }) {
					vkCmdFillBuffer(recorder.commandBuffer, buffer.vkBuffer, buffer.offset, buffer.size, 123456);
					recorder.bufferBarrier(buffer, ResourceUsage.TRANSFER_DEST, ResourceUsage.HOST_READ);
				}
				recorder.transitionLayout(newImages[index], null, ResourceUsage.TRANSFER_DEST);
				recorder.clearColorImage(newImages[index].vkImage, 1f, 0f, 1f, 1f);
			}
		}).destroy();

		for (var newMappedBuffer : newMappedBuffers) {
			var intBuffer = newMappedBuffer.intBuffer();
			assertEquals(2500, intBuffer.remaining());
			while (intBuffer.hasRemaining()) {
				assertEquals(123456, intBuffer.get());
			}
		}

		newBlock.destroy(instance);
		instance.destroyInitialObjects();
	}
}
