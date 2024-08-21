package com.github.knokko.boiler.commands;

import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.synchronization.ResourceUsage;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memByteBuffer;
import static org.lwjgl.system.MemoryUtil.memGetByte;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.VK_API_VERSION_1_2;

public class TestCommandRecorder {

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

			recorder.transitionColorLayout(sourceImage.vkImage(), null, ResourceUsage.TRANSFER_DEST);
			recorder.clearColorImage(sourceImage.vkImage(), 0f, 1f, 1f, 1f);
			recorder.transitionColorLayout(sourceImage.vkImage(), ResourceUsage.TRANSFER_DEST, ResourceUsage.TRANSFER_SOURCE);

			recorder.transitionColorLayout(destImage.vkImage(), null, ResourceUsage.TRANSFER_DEST);
			recorder.copyImage(width, height, VK_IMAGE_ASPECT_COLOR_BIT, sourceImage.vkImage(), destImage.vkImage());
			recorder.transitionColorLayout(destImage.vkImage(), ResourceUsage.TRANSFER_DEST, ResourceUsage.TRANSFER_SOURCE);
			recorder.copyImageToBuffer(VK_IMAGE_ASPECT_COLOR_BIT, destImage.vkImage(), width, height, destBuffer.vkBuffer());

			recorder.end("Copying");

			instance.queueFamilies().graphics().first().submit(commandBuffer, "Copying", null, fence);
			fence.waitAndReset();

			assertEquals((byte) 0, memGetByte(destBuffer.hostAddress()));
			assertEquals((byte) 255, memGetByte(destBuffer.hostAddress() + 1));
			assertEquals((byte) 255, memGetByte(destBuffer.hostAddress() + 2));
			assertEquals((byte) 255, memGetByte(destBuffer.hostAddress() + 3));
		}

		instance.sync.fenceBank.returnFence(fence);
		vkDestroyCommandPool(instance.vkDevice(), commandPool, null);
		destBuffer.destroy(instance);
		sourceImage.destroy(instance);
		destImage.destroy(instance);
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

		var commandPool = instance.commands.createPool(0, instance.queueFamilies().graphics().index(), "CopyPool");
		var commandBuffer = instance.commands.createPrimaryBuffers(commandPool, 1, "CopyCommandBuffer")[0];
		var fence = instance.sync.fenceBank.borrowFence(false, "CopyFence");

		try (var stack = stackPush()) {
			var recorder = CommandRecorder.begin(commandBuffer, instance, stack, "Blitting");

			recorder.transitionColorLayout(sourceImage.vkImage(), null, ResourceUsage.TRANSFER_DEST);
			recorder.copyBufferToImage(VK_IMAGE_ASPECT_COLOR_BIT, sourceImage.vkImage(), width1, height1, buffer.vkBuffer());
			recorder.transitionColorLayout(sourceImage.vkImage(), ResourceUsage.TRANSFER_DEST, ResourceUsage.TRANSFER_SOURCE);
			recorder.transitionColorLayout(destImage.vkImage(), null, ResourceUsage.TRANSFER_DEST);

			recorder.blitImage(
					VK_IMAGE_ASPECT_COLOR_BIT, VK_FILTER_LINEAR,
					sourceImage.vkImage(), width1, height1,
					destImage.vkImage(), width2, height2
			);

			recorder.transitionColorLayout(destImage.vkImage(), ResourceUsage.TRANSFER_DEST, ResourceUsage.TRANSFER_SOURCE);
			recorder.copyImageToBuffer(VK_IMAGE_ASPECT_COLOR_BIT, destImage.vkImage(), width2, height2, buffer.vkBuffer());
			recorder.end("Copying");

			var hostBuffer = memByteBuffer(buffer.hostAddress(), 4 * width1 * height1);
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

			instance.queueFamilies().graphics().first().submit(commandBuffer, "Copying", null, fence);
			fence.waitAndReset();

			// So the blitted pixel should be (25, 0, 50, 255)
			assertEquals((byte) 25, hostBuffer.get(0));
			assertEquals((byte) 0, hostBuffer.get(1));
			assertEquals((byte) 50, hostBuffer.get(2));
			assertEquals((byte) 255, hostBuffer.get(3));
		}

		instance.sync.fenceBank.returnFence(fence);
		vkDestroyCommandPool(instance.vkDevice(), commandPool, null);
		buffer.destroy(instance);
		sourceImage.destroy(instance);
		destImage.destroy(instance);
		instance.destroyInitialObjects();
	}
}
