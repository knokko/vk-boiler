package com.github.knokko.boiler.buffers;

import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.builders.device.SimpleDeviceSelector;
import com.github.knokko.boiler.commands.SingleTimeCommands;
import com.github.knokko.boiler.images.ImageBuilder;
import com.github.knokko.boiler.memory.SharedMemoryBuilder;
import com.github.knokko.boiler.synchronization.ResourceUsage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.vulkan.VK10.*;

public class TestBufferCopies {

	@Test
	public void testBufferCopiesAndSharedMemoryBuilder() {
		int[] deviceTypes = {
				VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU,
				VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU,
				VK_PHYSICAL_DEVICE_TYPE_CPU
		};
		boolean[] vmaChoices = { false, true };

		for (int deviceType : deviceTypes) {
			for (boolean useVma : vmaChoices) {
				var instance = new BoilerBuilder(
						VK_API_VERSION_1_0, "Test buffer copies", VK_MAKE_VERSION(1, 0, 0)
				).physicalDeviceSelector(new SimpleDeviceSelector(deviceType)).validation().forbidValidationErrors().build();
				var mappedBufferBuilder = new SharedMappedBufferBuilder(instance);
				int bufferUsage = VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_TRANSFER_SRC_BIT;

				var sourceBuffer = mappedBufferBuilder.add(100L, 1L);
				var middleBuffer = instance.buffers.createRaw(100, bufferUsage, "middle");
				var destinationBuffer = mappedBufferBuilder.add(100L, 1L);

				var sharedMemoryBuilder = new SharedMemoryBuilder(instance);
				sharedMemoryBuilder.add(middleBuffer);
				sharedMemoryBuilder.add(mappedBufferBuilder, bufferUsage, "SharedMappedBuffer");
				var sharedMemory = sharedMemoryBuilder.allocate("SharedMemory", useVma);

				var sourceHostBuffer = sourceBuffer.get().byteBuffer();
				for (int index = 0; index < 100; index++) {
					sourceHostBuffer.put((byte) index);
				}

				var commands = new SingleTimeCommands(instance);
				commands.submit("Copying", recorder -> {
					recorder.copyBufferRanges(sourceBuffer.get().range(), middleBuffer.fullRange());
					recorder.bufferBarrier(middleBuffer.fullRange(), ResourceUsage.TRANSFER_DEST, ResourceUsage.TRANSFER_SOURCE);
					recorder.copyBufferRanges(middleBuffer.fullRange(), destinationBuffer.get().range());
				});
				commands.destroy();

				var destinationHostBuffer = destinationBuffer.get().byteBuffer();
				for (int index = 0; index < 100; index++) {
					assertEquals((byte) index, destinationHostBuffer.get());
				}

				sharedMemory.free(instance);
				instance.destroyInitialObjects();
			}
		}
	}

	@Test
	public void testCopyBufferToBcImageDoesNotCauseValidationError() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_0, "Test buffer-to-bc copy", VK_MAKE_VERSION(1, 0, 0)
		).validation().forbidValidationErrors().build();

		var sourceBuffer = instance.buffers.createMapped(
				100, VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT, "source"
		);

		var destinationImage = new ImageBuilder(
				"Destination", 1, 3
		).texture().addUsage(VK_IMAGE_USAGE_TRANSFER_SRC_BIT).format(VK_FORMAT_BC1_RGBA_SRGB_BLOCK).build(instance);

		var commands = new SingleTimeCommands(instance);
		commands.submit("Copying", recorder -> {
			recorder.transitionLayout(destinationImage, null, ResourceUsage.TRANSFER_DEST);
			recorder.copyBufferToImage(destinationImage, sourceBuffer.fullRange());
			recorder.transitionLayout(destinationImage, ResourceUsage.TRANSFER_DEST, ResourceUsage.TRANSFER_SOURCE);
			recorder.copyImageToBuffer(destinationImage, sourceBuffer.fullRange());
		}).awaitCompletion();

		commands.destroy();
		sourceBuffer.destroy(instance);
		destinationImage.destroy(instance);
		instance.destroyInitialObjects();
	}
}
