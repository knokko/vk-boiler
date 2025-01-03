package com.github.knokko.boiler.buffers;

import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.commands.SingleTimeCommands;
import com.github.knokko.boiler.synchronization.ResourceUsage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.system.MemoryUtil.memByteBuffer;
import static org.lwjgl.vulkan.VK10.*;

public class TestBufferCopies {

	@Test
	public void testBufferCopies() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_0, "Test buffer copies", VK_MAKE_VERSION(1, 0, 0)
		).validation().forbidValidationErrors().build();

		var sourceBuffer = instance.buffers.createMapped(
				100, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, "source"
		);
		var sourceHostBuffer = memByteBuffer(sourceBuffer.hostAddress(), 100);
		var middleBuffer = instance.buffers.create(
				100, VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_TRANSFER_SRC_BIT, "middle"
		);
		var destinationBuffer = instance.buffers.createMapped(
				100, VK_BUFFER_USAGE_TRANSFER_DST_BIT, "destination"
		);
		var destinationHostBuffer = memByteBuffer(destinationBuffer.hostAddress(), 100);

		for (int index = 0; index < 100; index++) {
			sourceHostBuffer.put((byte) index);
		}

		var commands = new SingleTimeCommands(instance);
		commands.submit("Copying", recorder -> {
			recorder.copyBuffer(sourceBuffer.fullRange(), middleBuffer.vkBuffer(), 0);
			recorder.bufferBarrier(middleBuffer.fullRange(), ResourceUsage.TRANSFER_DEST, ResourceUsage.TRANSFER_SOURCE);
			recorder.copyBuffer(middleBuffer.fullRange(), destinationBuffer.vkBuffer(), 0);
		}).awaitCompletion();

		for (int index = 0; index < 100; index++) {
			assertEquals((byte) index, destinationHostBuffer.get());
		}

		commands.destroy();
		sourceBuffer.destroy(instance);
		middleBuffer.destroy(instance);
		destinationBuffer.destroy(instance);
		instance.destroyInitialObjects();
	}

	@Test
	public void testCopyBufferToBcImageDoesNotCauseValidationError() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_0, "Test buffer-to-bc copy", VK_MAKE_VERSION(1, 0, 0)
		).validation().forbidValidationErrors().build();

		var sourceBuffer = instance.buffers.createMapped(
				100, VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT, "source"
		);

		var destinationImage = instance.images.createSimple(
				1, 3, VK_FORMAT_BC1_RGBA_SRGB_BLOCK,
				VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
				VK_IMAGE_ASPECT_COLOR_BIT, "destination"
		);

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
