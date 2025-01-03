package com.github.knokko.boiler.commands;

import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.synchronization.ResourceUsage;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.VK_API_VERSION_1_2;

public class TestCommandRecorder {

	@Test
	public void testAlreadyRecording() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_0, "TestAlreadyRecording", 1
		).validation().forbidValidationErrors().build();

		var buffer = instance.buffers.createMapped(
				8, VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT, "TestBuffer"
		);
		memPutInt(buffer.hostAddress(), 1234);
		var commandPool = instance.commands.createPool(0, instance.queueFamilies().graphics().index(), "CopyPool");
		var commandBuffer = instance.commands.createPrimaryBuffers(commandPool, 1, "CopyCommandBuffer")[0];
		var fence = instance.sync.fenceBank.borrowFence(false, "CopyFence");
		try (var stack = stackPush()) {

			var biCommands = VkCommandBufferBeginInfo.calloc(stack);
			biCommands.sType$Default();
			biCommands.flags(0);
			biCommands.pInheritanceInfo(null);
			assertVkSuccess(vkBeginCommandBuffer(
					commandBuffer, biCommands
			), "BeginCommandBuffer", "Copying");
			var recorder = CommandRecorder.alreadyRecording(commandBuffer, instance, stack);
			recorder.copyBuffer(buffer.range(0, 4), buffer.vkBuffer(), 4);
			recorder.end("Copying");

			instance.queueFamilies().transfer().first().submit(commandBuffer, "Copying", null, fence);
			fence.awaitSignal();
		}

		assertEquals(1234, memGetInt(buffer.hostAddress() + 4));

		instance.sync.fenceBank.returnFence(fence);
		vkDestroyCommandPool(instance.vkDevice(), commandPool, null);
		buffer.destroy(instance);
		instance.destroyInitialObjects();
	}

	@Test
	public void testCopyImage() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_2, "TestCopyImage", 1
		).validation().forbidValidationErrors().build();

		int width = 10;
		int height = 5;
		var format = VK_FORMAT_R8G8B8A8_UNORM;
		var imageUsage = VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT;

		var destBuffer = instance.buffers.createMapped(
				4 * width * height, VK_BUFFER_USAGE_TRANSFER_DST_BIT, "DestBuffer"
		);

		var sourceImage = instance.images.create(
				width, height, format, imageUsage, VK_IMAGE_ASPECT_COLOR_BIT,
				VK_SAMPLE_COUNT_1_BIT, 1, 1, false, "SourceImage"
		);
		var destImage = instance.images.create(
				width, height, format, imageUsage, VK_IMAGE_ASPECT_COLOR_BIT,
				VK_SAMPLE_COUNT_1_BIT, 1, 1, false, "DestImage"
		);

		var commands = new SingleTimeCommands(instance);
		commands.submit("Copying", recorder -> {
			recorder.transitionLayout(sourceImage, null, ResourceUsage.TRANSFER_DEST);
			recorder.clearColorImage(sourceImage.vkImage(), 0f, 1f, 1f, 1f);
			recorder.transitionLayout(sourceImage, ResourceUsage.TRANSFER_DEST, ResourceUsage.TRANSFER_SOURCE);

			recorder.transitionLayout(destImage, null, ResourceUsage.TRANSFER_DEST);
			recorder.copyImage(sourceImage, destImage);
			recorder.transitionLayout(destImage, ResourceUsage.TRANSFER_DEST, ResourceUsage.TRANSFER_SOURCE);
			recorder.copyImageToBuffer(destImage, destBuffer.fullRange());
		}).awaitCompletion();

		assertEquals((byte) 0, memGetByte(destBuffer.hostAddress()));
		assertEquals((byte) 255, memGetByte(destBuffer.hostAddress() + 1));
		assertEquals((byte) 255, memGetByte(destBuffer.hostAddress() + 2));
		assertEquals((byte) 255, memGetByte(destBuffer.hostAddress() + 3));

		commands.destroy();
		destBuffer.destroy(instance);
		sourceImage.destroy(instance);
		destImage.destroy(instance);
		instance.destroyInitialObjects();
	}

	@Test
	public void testCopyBcImageDoesNotCauseValidationError() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_0, "Test bc image copy", VK_MAKE_VERSION(1, 0, 0)
		).validation().forbidValidationErrors().build();

		var sourceImage = instance.images.createSimple(
				1, 3, VK_FORMAT_BC1_RGBA_SRGB_BLOCK,
				VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
				VK_IMAGE_ASPECT_COLOR_BIT, "source"
		);

		var destinationImage = instance.images.createSimple(
				1, 3, VK_FORMAT_BC1_RGBA_SRGB_BLOCK,
				VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
				VK_IMAGE_ASPECT_COLOR_BIT, "destination"
		);

		var commands = new SingleTimeCommands(instance);
		commands.submit("Copying", recorder -> {
			recorder.transitionLayout(sourceImage, null, ResourceUsage.TRANSFER_SOURCE);
			recorder.transitionLayout(destinationImage, null, ResourceUsage.TRANSFER_DEST);
			recorder.copyImage(sourceImage, destinationImage);
		}).awaitCompletion();

		commands.destroy();
		sourceImage.destroy(instance);
		destinationImage.destroy(instance);
		instance.destroyInitialObjects();
	}

	@Test
	public void testBlitImage() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_2, "TestCopyImage", 1
		).validation().forbidValidationErrors().build();

		int width1 = 2;
		int height1 = 2;
		int width2 = 1;
		int height2 = 1;

		var format = VK_FORMAT_R8G8B8A8_UNORM;
		var imageUsage = VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT;

		var buffer = instance.buffers.createMapped(
				4 * width1 * height1,
				VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT, "DestBuffer"
		);

		var sourceImage = instance.images.create(
				width1, height1, format, imageUsage, VK_IMAGE_ASPECT_COLOR_BIT,
				VK_SAMPLE_COUNT_1_BIT, 1, 1, false, "SourceImage"
		);
		var destImage = instance.images.create(
				width2, height2, format, imageUsage, VK_IMAGE_ASPECT_COLOR_BIT,
				VK_SAMPLE_COUNT_1_BIT, 1, 1, false, "DestImage"
		);

		var hostBuffer = memByteBuffer(buffer.hostAddress(), 4 * width1 * height1);

		var commands = new SingleTimeCommands(instance);
		commands.submit("Blitting", recorder -> {
			recorder.transitionLayout(sourceImage, null, ResourceUsage.TRANSFER_DEST);
			recorder.copyBufferToImage(sourceImage, buffer.fullRange());
			recorder.transitionLayout(sourceImage, ResourceUsage.TRANSFER_DEST, ResourceUsage.TRANSFER_SOURCE);
			recorder.transitionLayout(destImage, null, ResourceUsage.TRANSFER_DEST);

			recorder.blitImage(VK_FILTER_LINEAR, sourceImage, destImage);

			recorder.transitionLayout(destImage, ResourceUsage.TRANSFER_DEST, ResourceUsage.TRANSFER_SOURCE);
			recorder.copyImageToBuffer(destImage, buffer.fullRange());

			// First pixel is (R, G, B, A) = (100, 0, 200, 255)
			hostBuffer.put(0, (byte) 100);
			hostBuffer.put(1, (byte) 0);
			hostBuffer.put(2, (byte) 200);
			hostBuffer.put(3, (byte) 255);

			// The other 3 pixels are (0, 0, 0, 255)
			for (int index = 4; index <= 12; index += 4) {
				hostBuffer.put(index, (byte) 0);
				hostBuffer.put(index + 1, (byte) 0);
				hostBuffer.put(index + 2, (byte) 0);
				hostBuffer.put(index + 3, (byte) 255);
			}
		}).awaitCompletion();

		// So the blitted pixel should be (25, 0, 50, 255)
		assertEquals((byte) 25, hostBuffer.get(0));
		assertEquals((byte) 0, hostBuffer.get(1));
		assertEquals((byte) 50, hostBuffer.get(2));
		assertEquals((byte) 255, hostBuffer.get(3));

		commands.destroy();
		buffer.destroy(instance);
		sourceImage.destroy(instance);
		destImage.destroy(instance);
		instance.destroyInitialObjects();
	}
}
