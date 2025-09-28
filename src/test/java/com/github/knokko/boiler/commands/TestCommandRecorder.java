package com.github.knokko.boiler.commands;

import com.github.knokko.boiler.buffers.MappedVkbBuffer;
import com.github.knokko.boiler.buffers.VkbBuffer;
import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.descriptors.BulkDescriptorUpdater;
import com.github.knokko.boiler.descriptors.DescriptorCombiner;
import com.github.knokko.boiler.descriptors.DescriptorSetLayoutBuilder;
import com.github.knokko.boiler.descriptors.VkbDescriptorSetLayout;
import com.github.knokko.boiler.images.ImageBuilder;
import com.github.knokko.boiler.images.VkbImage;
import com.github.knokko.boiler.memory.MemoryCombiner;
import com.github.knokko.boiler.pipelines.GraphicsPipelineBuilder;
import com.github.knokko.boiler.synchronization.ResourceUsage;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static com.github.knokko.boiler.utilities.BoilerMath.leastCommonMultiple;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.VK_API_VERSION_1_1;
import static org.lwjgl.vulkan.VK12.VK_API_VERSION_1_2;

public class TestCommandRecorder {

	@Test
	public void testAlreadyRecording() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_0, "TestAlreadyRecording", 1
		).validation().forbidValidationErrors().build();

		var combiner = new MemoryCombiner(instance, "TestMemory");
		var buffer = combiner.addMappedBuffer(
				8, 4, VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT
		);
		var memory = combiner.build(false);

		memPutInt(buffer.hostAddress, 1234);
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
			recorder.copyBuffer(buffer.child(0, 4), buffer.child(4, 4));
			recorder.end("Copying");

			instance.queueFamilies().transfer().first().submit(commandBuffer, "Copying", null, fence);
			fence.awaitSignal();
		}

		assertEquals(1234, memGetInt(buffer.hostAddress + 4));

		instance.sync.fenceBank.returnFence(fence);
		vkDestroyCommandPool(instance.vkDevice(), commandPool, null);
		memory.destroy(instance);
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

		var combiner = new MemoryCombiner(instance, "TestMemory");
		var destinationBuffer = combiner.addMappedDeviceLocalBuffer(
				4 * width * height, 4, VK_BUFFER_USAGE_TRANSFER_DST_BIT, 0.5f
		);
		var sourceImage = combiner.addImage(new ImageBuilder(
				"SourceImage", width, height
		).format(format).setUsage(imageUsage).doNotCreateView(), 1f);
		var destinationImage = combiner.addImage(new ImageBuilder(
				"DestinationImage", width, height
		).format(format).setUsage(imageUsage).doNotCreateView(), 1f);
		var memory = combiner.build(false);

		SingleTimeCommands.submit(instance, "Copying", recorder -> {
			recorder.transitionLayout(sourceImage, null, ResourceUsage.TRANSFER_DEST);
			recorder.clearColorImage(sourceImage.vkImage, 0f, 1f, 1f, 1f);
			recorder.transitionLayout(sourceImage, ResourceUsage.TRANSFER_DEST, ResourceUsage.TRANSFER_SOURCE);

			recorder.transitionLayout(destinationImage, null, ResourceUsage.TRANSFER_DEST);
			recorder.copyImage(sourceImage, destinationImage);
			recorder.transitionLayout(destinationImage, ResourceUsage.TRANSFER_DEST, ResourceUsage.TRANSFER_SOURCE);
			recorder.copyImageToBuffer(destinationImage, destinationBuffer);
		}).destroy();

		assertEquals((byte) 0, memGetByte(destinationBuffer.hostAddress));
		assertEquals((byte) 255, memGetByte(destinationBuffer.hostAddress + 1));
		assertEquals((byte) 255, memGetByte(destinationBuffer.hostAddress + 2));
		assertEquals((byte) 255, memGetByte(destinationBuffer.hostAddress + 3));

		memory.destroy(instance);
		instance.destroyInitialObjects();
	}

	@Test
	public void testCopyBcImageDoesNotCauseValidationError() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_0, "Test bc image copy", VK_MAKE_VERSION(1, 0, 0)
		).validation().forbidValidationErrors().build();

		var combiner = new MemoryCombiner(instance, "TestMemory");
		var sourceImage = combiner.addImage(new ImageBuilder(
				"Source", 1, 3
		).format(VK_FORMAT_BC1_RGBA_SRGB_BLOCK).setUsage(VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_SAMPLED_BIT), 1f);
		var destinationImage = combiner.addImage(new ImageBuilder(
				"Destination", 1, 3
		).texture().format(VK_FORMAT_BC1_RGBA_SRGB_BLOCK), 1f);
		var memory = combiner.build(true);

		SingleTimeCommands.submit(instance, "Copying", recorder -> {
			recorder.transitionLayout(sourceImage, null, ResourceUsage.TRANSFER_SOURCE);
			recorder.transitionLayout(destinationImage, null, ResourceUsage.TRANSFER_DEST);
			recorder.copyImage(sourceImage, destinationImage);
		}).destroy();

		memory.destroy(instance);
		instance.destroyInitialObjects();
	}

	@Test
	public void testBlitImageAndSharedMemoryBuilder() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_2, "TestCopyImage", 1
		).validation().forbidValidationErrors().build();

		int width1 = 2;
		int height1 = 2;
		int width2 = 1;
		int height2 = 1;

		var format = VK_FORMAT_R8G8B8A8_UNORM;
		var imageUsage = VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT;

		for (boolean useVma : new boolean[] { false, true }) {
			var combiner = new MemoryCombiner(instance, "TestMemory" + useVma);
			var hostBuffer = combiner.addMappedBuffer(
					4 * width1 * height1, 4,
					VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT
			);
			var sourceImage = combiner.addImage(new ImageBuilder(
					"SourceImage", width1, height1
			).format(format).setUsage(imageUsage).doNotCreateView(), 1f);
			var destinationImage = combiner.addImage(new ImageBuilder(
					"DestinationImage", width2, height2
			).format(format).setUsage(imageUsage).doNotCreateView(), 1f);
			var memory = combiner.build(useVma);

			var hostByteBuffer = hostBuffer.byteBuffer();
			SingleTimeCommands.submit(instance, "Blitting", recorder -> {
				recorder.transitionLayout(sourceImage, null, ResourceUsage.TRANSFER_DEST);
				recorder.copyBufferToImage(sourceImage, hostBuffer);
				recorder.transitionLayout(sourceImage, ResourceUsage.TRANSFER_DEST, ResourceUsage.TRANSFER_SOURCE);
				recorder.transitionLayout(destinationImage, null, ResourceUsage.TRANSFER_DEST);

				recorder.blitImage(VK_FILTER_LINEAR, sourceImage, destinationImage);

				recorder.transitionLayout(destinationImage, ResourceUsage.TRANSFER_DEST, ResourceUsage.TRANSFER_SOURCE);
				recorder.copyImageToBuffer(destinationImage, hostBuffer);

				// First pixel is (R, G, B, A) = (100, 0, 200, 255)
				hostByteBuffer.put(0, (byte) 100);
				hostByteBuffer.put(1, (byte) 0);
				hostByteBuffer.put(2, (byte) 200);
				hostByteBuffer.put(3, (byte) 255);

				// The other 3 pixels are (0, 0, 0, 255)
				for (int index = 4; index <= 12; index += 4) {
					hostByteBuffer.put(index, (byte) 0);
					hostByteBuffer.put(index + 1, (byte) 0);
					hostByteBuffer.put(index + 2, (byte) 0);
					hostByteBuffer.put(index + 3, (byte) 255);
				}
			}).destroy();

			// So the blitted pixel should be (25, 0, 50, 255)
			assertEquals((byte) 25, hostByteBuffer.get(0));
			assertEquals((byte) 0, hostByteBuffer.get(1));
			assertEquals((byte) 50, hostByteBuffer.get(2));
			assertEquals((byte) 255, hostByteBuffer.get(3));

			memory.destroy(instance);
		}

		instance.destroyInitialObjects();
	}

	@Test
	public void testBulkOperations() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_2, "TestBulkOperations", 1
		).validation().forbidValidationErrors().build();

		for (int amount : new int[] { 0, 1, 200, 2002 }) {
			var combiner = new MemoryCombiner(instance, "Memory" + amount);
			var sourceBuffers = new MappedVkbBuffer[amount];
			var middleBuffers = new VkbBuffer[amount];
			var images1 = new VkbImage[amount];
			var images2 = new VkbImage[amount];
			var destinationBuffers = new MappedVkbBuffer[amount];

			for (int index = 0; index < amount; index++) {
				sourceBuffers[index] = combiner.addMappedBuffer(4L, 4L, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
				middleBuffers[index] = combiner.addBuffer(
						4L, 4L, VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT, 0.5f
				);
				images1[index] = combiner.addImage(new ImageBuilder(
						"Test1Image" + index, 1, 1
				).texture().addUsage(VK_IMAGE_USAGE_TRANSFER_SRC_BIT).format(VK_FORMAT_R8G8B8A8_UNORM), 1f);
				images2[index] = combiner.addImage(new ImageBuilder(
						"Test2Image" + index, 1, 1
				).texture().addUsage(VK_IMAGE_USAGE_TRANSFER_SRC_BIT).format(VK_FORMAT_R8G8B8A8_UNORM), 1f);
				destinationBuffers[index] = combiner.addMappedBuffer(4L, 4L, VK_BUFFER_USAGE_TRANSFER_DST_BIT);
			}
			var memory = combiner.build(true);
			for (int index = 0; index < amount; index++) sourceBuffers[index].intBuffer().put(index);

			SingleTimeCommands.submit(instance, "Bulk", recorder -> {
				recorder.bulkCopyBuffers(sourceBuffers, middleBuffers);
				recorder.bulkBufferBarrier(ResourceUsage.TRANSFER_DEST, ResourceUsage.TRANSFER_SOURCE, middleBuffers);
				recorder.bulkTransitionLayout(null, ResourceUsage.TRANSFER_DEST, images1);
				recorder.bulkCopyBufferToImage(images1, middleBuffers);
				recorder.bulkTransitionLayout(ResourceUsage.TRANSFER_DEST, ResourceUsage.TRANSFER_SOURCE, images1);
				recorder.bulkTransitionLayout(null, ResourceUsage.TRANSFER_DEST, images2);
				recorder.bulkCopyImages(images1, images2);
				recorder.bulkTransitionLayout(ResourceUsage.TRANSFER_DEST, ResourceUsage.TRANSFER_SOURCE, images2);
				recorder.bulkCopyImageToBuffers(images2, destinationBuffers);
				recorder.bulkBufferBarrier(ResourceUsage.TRANSFER_DEST, ResourceUsage.HOST_READ, destinationBuffers);
			}).destroy();

			for (int index = 0; index < amount; index++) assertEquals(index, destinationBuffers[index].intBuffer().get());
			memory.destroy(instance);
		}

		instance.destroyInitialObjects();
	}

	@Test
	public void doNotOverflowMemoryStack1() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_1, "CheckStackOverflow1", 1
		).validation().forbidValidationErrors().build();

		int amount = 250;
		var combiner = new MemoryCombiner(instance, "OverflowMemory");
		VkbImage[] images1 = new VkbImage[amount];
		VkbImage[] images2 = new VkbImage[amount];
		VkbBuffer[] buffers1 = new VkbBuffer[amount];
		MappedVkbBuffer[] buffers2 = new MappedVkbBuffer[amount];

		for (int counter = 0; counter < amount; counter++) {
			images1[counter] = combiner.addImage(new ImageBuilder(
					"Image1." + counter, 5, 6
			).format(VK_FORMAT_R8G8B8A8_SRGB).setUsage(
					VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT
			).doNotCreateView(), 1f);
			images2[counter] = combiner.addImage(new ImageBuilder(
					"Image1." + counter, 5, 6
			).format(VK_FORMAT_R8G8B8A8_SRGB).setUsage(
					VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT
			).doNotCreateView(), 1f);

			buffers1[counter] = combiner.addBuffer(
					120L, 36L,
					VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
					1f
			);
			buffers2[counter] = combiner.addMappedBuffer(
					120L, 36L,
					VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT
			);
		}
		var memory = combiner.build(true);

		SingleTimeCommands.submit(instance, "TestStackRecorder", recorder -> {
			recorder.bulkTransitionLayout(null, ResourceUsage.TRANSFER_DEST, images1);
			for (int index = 0; index < amount; index++) {
				recorder.clearColorImage(images1[index].vkImage, 1f, 0f, 1f, 1f);
				recorder.transitionLayout(images2[index], null, ResourceUsage.TRANSFER_DEST);
				recorder.clearColorImage(images2[index].vkImage, 0f, 1f, 1f, 1f);
				recorder.transitionLayout(images2[index], ResourceUsage.TRANSFER_DEST, ResourceUsage.TRANSFER_SOURCE);
				recorder.copyImageToBuffer(images2[index], buffers2[index]);
				recorder.bufferBarrier(buffers2[index], ResourceUsage.TRANSFER_DEST, ResourceUsage.TRANSFER_SOURCE);
			}
			recorder.bulkTransitionLayout(ResourceUsage.TRANSFER_DEST, ResourceUsage.TRANSFER_SOURCE, images1);
			recorder.bulkCopyImageToBuffers(images1, buffers1);

			for (int index = 0; index < amount; index++) {
				recorder.transitionLayout(images1[index], ResourceUsage.TRANSFER_SOURCE, ResourceUsage.TRANSFER_DEST);
				recorder.copyImage(images2[index], images1[index]);
				recorder.transitionLayout(images1[index], ResourceUsage.TRANSFER_DEST, ResourceUsage.TRANSFER_SOURCE);
				recorder.transitionLayout(images2[index], ResourceUsage.TRANSFER_SOURCE, ResourceUsage.TRANSFER_DEST);
				recorder.blitImage(VK_FILTER_NEAREST, images1[index], images2[index]);
				recorder.transitionLayout(images1[index], ResourceUsage.TRANSFER_SOURCE, ResourceUsage.TRANSFER_DEST);
				recorder.copyBufferToImage(images1[index], buffers2[index]);

				recorder.transitionLayout(images2[index], ResourceUsage.TRANSFER_DEST, ResourceUsage.TRANSFER_SOURCE);
				recorder.bufferBarrier(buffers2[index], ResourceUsage.TRANSFER_DEST, ResourceUsage.TRANSFER_SOURCE);
				recorder.copyImageToBuffer(images2[index], buffers2[index]);
				recorder.transitionLayout(images2[index], ResourceUsage.TRANSFER_SOURCE, ResourceUsage.TRANSFER_DEST);
			}

			recorder.bulkTransitionLayout(ResourceUsage.TRANSFER_DEST, ResourceUsage.TRANSFER_SOURCE, images1);
			recorder.bulkBlitImages(VK_FILTER_LINEAR, images1, images2);
			recorder.bulkBufferBarrier(ResourceUsage.TRANSFER_DEST, ResourceUsage.HOST_READ, buffers2);
		}).destroy();

		for (MappedVkbBuffer buffer : buffers2) {
			ByteBuffer color = buffer.byteBuffer();
			assertEquals(0, color.get()); // Red
			assertEquals(-1, color.get()); // Green
			assertEquals(-1, color.get()); // Blue
			assertEquals(-1, color.get()); // Alpha
		}
		memory.destroy(instance);
		instance.destroyInitialObjects();
	}

	@Test
	@SuppressWarnings("resource")
	public void testDoNotOverflowMemoryStack2() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_1, "CheckStackOverflow1", 1
		).validation().forbidValidationErrors().enableDynamicRendering().build();

		int amount = 2500;
		var combiner = new MemoryCombiner(instance, "OverflowMemory");

		var colorImages = new VkbImage[amount];
		var vertexBuffers = new MappedVkbBuffer[amount];
		var colorBuffers = new MappedVkbBuffer[amount];
		var destinationBuffers = new MappedVkbBuffer[amount];

		for (int index = 0; index < amount; index++) {
			colorImages[index] = combiner.addImage(new ImageBuilder(
					"Target" + index, 5, 5
			).colorAttachment().addUsage(VK_IMAGE_USAGE_TRANSFER_SRC_BIT), 1f);
			vertexBuffers[index] = combiner.addMappedBuffer(24, 4, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
			long colorAlignment = leastCommonMultiple(
					16, instance.deviceProperties.limits().minStorageBufferOffsetAlignment()
			);
			colorBuffers[index] = combiner.addMappedBuffer(48, colorAlignment, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
			destinationBuffers[index] = combiner.addMappedBuffer(100, 4, VK_BUFFER_USAGE_TRANSFER_DST_BIT);
		}
		var memory = combiner.build(false);

		for (int index = 0; index < amount; index++) {
			var vertexBuffer = vertexBuffers[index].floatBuffer();
			vertexBuffer.put(-1f).put(-1f);
			vertexBuffer.put(1f).put(-1f);
			vertexBuffer.put(1f).put(1f);

			var colorBuffer = colorBuffers[index].floatBuffer();
			colorBuffer.put(1f).put(0f).put(0f).put(1f);
			colorBuffer.put(0f).put(0f).put(1f).put(1f);
			colorBuffer.put(0f).put(1f).put(0f).put(1f);
		}

		VkbDescriptorSetLayout descriptorLayout;
		long graphicsPipeline, pipelineLayout;
		try (MemoryStack stack = stackPush()) {
			var descriptors = new DescriptorSetLayoutBuilder(stack, 1);
			descriptors.set(0, 0, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_SHADER_STAGE_FRAGMENT_BIT);
			descriptorLayout = descriptors.build(instance, "StackDescriptorLayout");

			var pushConstants = VkPushConstantRange.calloc(1, stack);
			pushConstants.get(0).set(VK_SHADER_STAGE_FRAGMENT_BIT, 0, 4);

			pipelineLayout = instance.pipelines.createLayout(
					pushConstants, "StackPipelineLayout", descriptorLayout.vkDescriptorSetLayout
			);

			var vertexAttributes = VkVertexInputAttributeDescription.calloc(1, stack);
			vertexAttributes.get(0).set(0, 0, VK_FORMAT_R32G32_SFLOAT, 0);

			var vertexBindings = VkVertexInputBindingDescription.calloc(1, stack);
			vertexBindings.get(0).set(0, 8, VK_VERTEX_INPUT_RATE_VERTEX);

			var vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack);
			vertexInput.sType$Default();
			vertexInput.pVertexAttributeDescriptions(vertexAttributes);
			vertexInput.pVertexBindingDescriptions(vertexBindings);

			var builder = new GraphicsPipelineBuilder(instance, stack);
			builder.simpleShaderStages(
					"Stack", "shaders/",
					"stack.vert.spv", "stack.frag.spv"
			);
			builder.ciPipeline.pVertexInputState(vertexInput);
			builder.simpleInputAssembly();
			builder.dynamicViewports(1);
			builder.simpleRasterization(VK_CULL_MODE_NONE);
			builder.noMultisampling();
			builder.noDepthStencil();
			builder.noColorBlending(1);
			builder.dynamicStates(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR);
			builder.ciPipeline.layout(pipelineLayout);
			builder.dynamicRendering(0, VK_FORMAT_UNDEFINED, VK_FORMAT_UNDEFINED, VK_FORMAT_R8G8B8A8_SRGB);
			graphicsPipeline = builder.build("StackPipeline");
		}

		var descriptors = new DescriptorCombiner(instance);
		long[] descriptorSets = descriptors.addMultiple(descriptorLayout, amount);
		long descriptorPool = descriptors.build("StackDescriptorPool");

		var updater = new BulkDescriptorUpdater(instance, null, amount, amount, 0);
		for (int index = 0; index < amount; index++) {
			updater.writeStorageBuffer(descriptorSets[index], 0, colorBuffers[index]);
		}
		updater.finish();

		SingleTimeCommands.submit(instance, "CheckOverflow", recorder -> {
			recorder.bulkTransitionLayout(null, ResourceUsage.COLOR_ATTACHMENT_WRITE, colorImages);
			var pushConstants = recorder.stack.ints(1);

			var colorAttachments = VkRenderingAttachmentInfo.calloc(1, recorder.stack);
			for (int index = 0; index < amount; index++) {
				recorder.simpleColorRenderingAttachment(
						colorAttachments.get(0),colorImages[index].vkImageView, VK_ATTACHMENT_LOAD_OP_CLEAR,
						VK_ATTACHMENT_STORE_OP_STORE, 0f, 0f, 0f, 0f
				);
				recorder.beginSimpleDynamicRendering(5, 5, colorAttachments, null, null);
				recorder.dynamicViewportAndScissor(5, 5);
				vkCmdBindPipeline(recorder.commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicsPipeline);
				recorder.bindVertexBuffers(0, vertexBuffers[index]);
				recorder.bindGraphicsDescriptors(pipelineLayout, descriptorSets[index]);
				vkCmdPushConstants(recorder.commandBuffer, pipelineLayout, VK_SHADER_STAGE_FRAGMENT_BIT, 0, pushConstants);
				vkCmdDraw(recorder.commandBuffer, 3, 1, 0, 0);
				recorder.endDynamicRendering();
			}
			recorder.bulkTransitionLayout(ResourceUsage.COLOR_ATTACHMENT_WRITE, ResourceUsage.TRANSFER_SOURCE, colorImages);
			recorder.bulkCopyImageToBuffers(colorImages, destinationBuffers);
			recorder.bulkBufferBarrier(ResourceUsage.TRANSFER_DEST, ResourceUsage.HOST_READ, destinationBuffers);
		}).destroy();

		for (int index = 0; index < amount; index++) {
			int numBluePixels = 0;
			ByteBuffer pixelBuffer = destinationBuffers[index].byteBuffer();

			for (int pixel = 0; pixel < 25; pixel++) {
				byte red = pixelBuffer.get();
				byte green = pixelBuffer.get();
				byte blue = pixelBuffer.get();
				byte alpha = pixelBuffer.get();

				assertEquals(0, red);
				assertEquals(0, green);
				if (blue == -1) {
					numBluePixels += 1;
					assertEquals(-1, alpha);
				} else {
					assertEquals(0, blue);
					assertEquals(0, alpha);
				}
			}

			assertTrue(
					numBluePixels > 5 && numBluePixels < 20,
					"Expected " + numBluePixels + " to be approximately 12"
			);
		}

		vkDestroyDescriptorPool(instance.vkDevice(), descriptorPool, null);
		vkDestroyPipeline(instance.vkDevice(), graphicsPipeline, null);
		vkDestroyPipelineLayout(instance.vkDevice(), pipelineLayout, null);
		vkDestroyDescriptorSetLayout(instance.vkDevice(), descriptorLayout.vkDescriptorSetLayout, null);
		memory.destroy(instance);

		instance.destroyInitialObjects();
	}
}
