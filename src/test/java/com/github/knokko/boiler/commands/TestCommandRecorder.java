package com.github.knokko.boiler.commands;

import com.github.knokko.boiler.builder.BoilerBuilder;
import com.github.knokko.boiler.images.VmaImage;
import com.github.knokko.boiler.sync.ResourceUsage;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VkClearColorValue;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkImageSubresourceRange;

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
		var boiler = new BoilerBuilder(
				VK_API_VERSION_1_2, "TestCopyImage", 1
		).validation().forbidValidationErrors().build();

		int width = 10;
		int height = 5;
		var format = VK_FORMAT_R8G8B8A8_UNORM;
		var imageUsage = VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT;

		var destBuffer = boiler.buffers.createMapped(
				4 * width * height, VK_BUFFER_USAGE_TRANSFER_DST_BIT, "DestBuffer"
		);

		VmaImage sourceImage;
		VmaImage destImage;
		try (var stack = stackPush()) {
			sourceImage = boiler.images.create(
					stack, width, height, format, imageUsage, VK_IMAGE_ASPECT_COLOR_BIT,
					VK_SAMPLE_COUNT_1_BIT, 1, 1, false, "SourceImage"
			);
			destImage = boiler.images.create(
					stack, width, height, format, imageUsage, VK_IMAGE_ASPECT_COLOR_BIT,
					VK_SAMPLE_COUNT_1_BIT, 1, 1, false, "DestImage"
			);
		}

		var commandPool = boiler.commands.createPool(0, boiler.queueFamilies().graphics().index(), "CopyPool");
		var commandBuffer = boiler.commands.createPrimaryBuffers(commandPool, 1, "CopyCommandBuffer")[0];
		var fence = boiler.sync.createFences(false, 1, "CopyFence")[0];

		try (var stack = stackPush()) {
			var biCommands = VkCommandBufferBeginInfo.calloc(stack);
			biCommands.sType$Default();
			biCommands.flags(0);
			biCommands.pInheritanceInfo(null);

			assertVkSuccess(vkBeginCommandBuffer(
					commandBuffer, biCommands
			), "BeginCommandBuffer", "Copying");

			var recorder = CommandRecorder.alreadyRecording(commandBuffer, boiler, stack);

			recorder.transitionColorLayout(
					sourceImage.vkImage(), VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, null,
					new ResourceUsage(VK_ACCESS_TRANSFER_WRITE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT)
			);

			var clearColor = VkClearColorValue.calloc(stack);
			clearColor.float32(0, 0f);
			clearColor.float32(1, 1f);
			clearColor.float32(2, 1f);
			clearColor.float32(3, 1f);

			var clearRange = VkImageSubresourceRange.calloc(1, stack);
			boiler.images.subresourceRange(stack, clearRange.get(0), VK_IMAGE_ASPECT_COLOR_BIT);

			vkCmdClearColorImage(commandBuffer, sourceImage.vkImage(), VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, clearColor, clearRange);

			recorder.transitionColorLayout(
					sourceImage.vkImage(), VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
					new ResourceUsage(VK_ACCESS_TRANSFER_WRITE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT),
					new ResourceUsage(VK_ACCESS_TRANSFER_READ_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT)
			);
			recorder.transitionColorLayout(
					destImage.vkImage(), VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, null,
					new ResourceUsage(VK_ACCESS_TRANSFER_WRITE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT)
			);

			recorder.copyImage(width, height, VK_IMAGE_ASPECT_COLOR_BIT, sourceImage.vkImage(), destImage.vkImage());

			recorder.transitionColorLayout(
					destImage.vkImage(), VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
					new ResourceUsage(VK_ACCESS_TRANSFER_WRITE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT),
					new ResourceUsage(VK_ACCESS_TRANSFER_READ_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT)
			);

			recorder.copyImageToBuffer(VK_IMAGE_ASPECT_COLOR_BIT, destImage.vkImage(), width, height, destBuffer.vkBuffer());

			recorder.end("Copying");
			boiler.queueFamilies().graphics().queues().get(0).submit(commandBuffer, "Copying", null, fence);
			boiler.sync.waitAndReset(stack, fence);

			assertEquals((byte) 0, memGetByte(destBuffer.hostAddress()));
			assertEquals((byte) 255, memGetByte(destBuffer.hostAddress() + 1));
			assertEquals((byte) 255, memGetByte(destBuffer.hostAddress() + 2));
			assertEquals((byte) 255, memGetByte(destBuffer.hostAddress() + 3));
		}

		vkDestroyFence(boiler.vkDevice(), fence, null);
		vkDestroyCommandPool(boiler.vkDevice(), commandPool, null);
		destBuffer.destroy(boiler.vmaAllocator());
		sourceImage.destroy(boiler);
		destImage.destroy(boiler);
		boiler.destroyInitialObjects();
	}

	@Test
	public void testBlitImage() {
		var boiler = new BoilerBuilder(
				VK_API_VERSION_1_2, "TestCopyImage", 1
		).validation().forbidValidationErrors().build();

		int width1 = 2;
		int height1 = 2;
		int width2 = 1;
		int height2 = 1;

		var format = VK_FORMAT_R8G8B8A8_UNORM;
		var imageUsage = VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT;

		var buffer = boiler.buffers.createMapped(
				4 * width1 * height1,
				VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT, "DestBuffer"
		);

		VmaImage sourceImage;
		VmaImage destImage;
		try (var stack = stackPush()) {
			sourceImage = boiler.images.create(
					stack, width1, height1, format, imageUsage, VK_IMAGE_ASPECT_COLOR_BIT,
					VK_SAMPLE_COUNT_1_BIT, 1, 1, false, "SourceImage"
			);
			destImage = boiler.images.create(
					stack, width2, height2, format, imageUsage, VK_IMAGE_ASPECT_COLOR_BIT,
					VK_SAMPLE_COUNT_1_BIT, 1, 1, false, "DestImage"
			);
		}

		var commandPool = boiler.commands.createPool(0, boiler.queueFamilies().graphics().index(), "CopyPool");
		var commandBuffer = boiler.commands.createPrimaryBuffers(commandPool, 1, "CopyCommandBuffer")[0];
		var fence = boiler.sync.createFences(false, 1, "CopyFence")[0];

		try (var stack = stackPush()) {
			var recorder = CommandRecorder.begin(commandBuffer, boiler, stack, "Blitting");

			recorder.transitionColorLayout(
					sourceImage.vkImage(), VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, null,
					new ResourceUsage(VK_ACCESS_TRANSFER_WRITE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT)
			);

			recorder.copyBufferToImage(VK_IMAGE_ASPECT_COLOR_BIT, sourceImage.vkImage(), width1, height1, buffer.vkBuffer());
			recorder.transitionColorLayout(
					sourceImage.vkImage(), VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
					new ResourceUsage(VK_ACCESS_TRANSFER_WRITE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT),
					new ResourceUsage(VK_ACCESS_TRANSFER_READ_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT)
			);
			recorder.transitionColorLayout(
					destImage.vkImage(), VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, null,
					new ResourceUsage(VK_ACCESS_TRANSFER_WRITE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT)
			);

			recorder.blitImage(
					VK_IMAGE_ASPECT_COLOR_BIT, VK_FILTER_LINEAR,
					sourceImage.vkImage(), width1, height1,
					destImage.vkImage(), width2, height2
			);

			recorder.transitionColorLayout(
					destImage.vkImage(), VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
					new ResourceUsage(VK_ACCESS_TRANSFER_WRITE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT),
					new ResourceUsage(VK_ACCESS_TRANSFER_READ_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT)
			);

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

			boiler.queueFamilies().graphics().queues().get(0).submit(commandBuffer, "Copying", null, fence);
			boiler.sync.waitAndReset(stack, fence);

			// So the blitted pixel should be (25, 0, 50, 255)
			assertEquals((byte) 25, hostBuffer.get(0));
			assertEquals((byte) 0, hostBuffer.get(1));
			assertEquals((byte) 50, hostBuffer.get(2));
			assertEquals((byte) 255, hostBuffer.get(3));
		}

		vkDestroyFence(boiler.vkDevice(), fence, null);
		vkDestroyCommandPool(boiler.vkDevice(), commandPool, null);
		buffer.destroy(boiler.vmaAllocator());
		sourceImage.destroy(boiler);
		destImage.destroy(boiler);
		boiler.destroyInitialObjects();
	}
}
