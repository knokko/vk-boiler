package com.github.knokko.boiler.buffers;

import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.builders.device.SimpleDeviceSelector;
import com.github.knokko.boiler.commands.SingleTimeCommands;
import com.github.knokko.boiler.images.ImageBuilder;
import com.github.knokko.boiler.memory.MemoryCombiner;
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

				var combiner = new MemoryCombiner(instance, "Memory" + deviceType + useVma);

				int sourceAndDestination = VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT;
				var sourceBuffer = combiner.addMappedDeviceLocalBuffer(100, 1, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
				var middleBuffer1 = combiner.addBuffer(100, 1, sourceAndDestination);
				var middleBuffer2 = combiner.addBuffer(100, 1, sourceAndDestination);
				var destinationBuffer = combiner.addMappedBuffer(100L, 1L, VK_BUFFER_USAGE_TRANSFER_DST_BIT);

				var memory = combiner.build(useVma);

				var sourceHostBuffer = sourceBuffer.byteBuffer();
				for (int index = 0; index < 100; index++) {
					sourceHostBuffer.put((byte) index);
				}

				SingleTimeCommands.submit(instance, "Copying", recorder -> {
					recorder.copyBuffer(sourceBuffer, middleBuffer1);
					recorder.bufferBarrier(middleBuffer1, ResourceUsage.TRANSFER_DEST, ResourceUsage.TRANSFER_SOURCE);
					recorder.copyBuffer(middleBuffer1, middleBuffer2);
					recorder.bufferBarrier(middleBuffer2, ResourceUsage.TRANSFER_DEST, ResourceUsage.TRANSFER_SOURCE);
					recorder.copyBuffer(middleBuffer2, destinationBuffer);
				}).destroy();

				var destinationHostBuffer = destinationBuffer.byteBuffer();
				for (int index = 0; index < 100; index++) {
					assertEquals((byte) index, destinationHostBuffer.get());
				}

				memory.destroy(instance);
				instance.destroyInitialObjects();
			}
		}
	}

	@Test
	public void testCopyBufferToBcImageDoesNotCauseValidationError() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_0, "Test buffer-to-bc copy", VK_MAKE_VERSION(1, 0, 0)
		).validation().forbidValidationErrors().build();

		var combiner = new MemoryCombiner(instance, "Memory");
		var sourceBuffer = combiner.addMappedBuffer(
				100, 8, VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT
		);

		var destinationImage = combiner.addImage(new ImageBuilder(
				"Destination", 1, 3
		).texture().addUsage(VK_IMAGE_USAGE_TRANSFER_SRC_BIT).format(VK_FORMAT_BC1_RGBA_SRGB_BLOCK));
		var memory = combiner.build(false);

		SingleTimeCommands.submit(instance, "Copying", recorder -> {
			recorder.transitionLayout(destinationImage, null, ResourceUsage.TRANSFER_DEST);
			recorder.copyBufferToImage(destinationImage, sourceBuffer);
			recorder.transitionLayout(destinationImage, ResourceUsage.TRANSFER_DEST, ResourceUsage.TRANSFER_SOURCE);
			recorder.copyImageToBuffer(destinationImage, sourceBuffer);
		}).destroy();

		memory.destroy(instance);
		instance.destroyInitialObjects();
	}
}
