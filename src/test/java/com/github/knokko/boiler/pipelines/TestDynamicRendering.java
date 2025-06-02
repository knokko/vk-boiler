package com.github.knokko.boiler.pipelines;

import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.commands.SingleTimeCommands;
import com.github.knokko.boiler.images.ImageBuilder;
import com.github.knokko.boiler.memory.MemoryBlockBuilder;
import com.github.knokko.boiler.synchronization.ResourceUsage;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;

import static com.github.knokko.boiler.utilities.ColorPacker.rgb;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memGetByte;
import static org.lwjgl.system.MemoryUtil.memGetFloat;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.VK_API_VERSION_1_1;
import static org.lwjgl.vulkan.VK12.VK_API_VERSION_1_2;
import static org.lwjgl.vulkan.VK12.VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK13.VK_API_VERSION_1_3;

public class TestDynamicRendering {

	private void testDynamicColorRendering(int apiVersion) {
		var instance = new BoilerBuilder(
				apiVersion, "TestDynamicColorRendering", apiVersion
		).validation().forbidValidationErrors().enableDynamicRendering().build();

		int width = 100;
		int height = 50;
		var format = VK_FORMAT_R8G8B8A8_UNORM;

		var builder = new MemoryBlockBuilder(instance, "Memory");
		var image = builder.addImage(new ImageBuilder("TestColorAttachment", width, height).format(format).setUsage(
				VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT
		));
		var destinationBuffer = builder.addMappedBuffer(4 * width * height, 4, VK_BUFFER_USAGE_TRANSFER_DST_BIT);
		var memory = builder.allocate(true);

		long pipelineLayout;
		long graphicsPipeline;
		try (var stack = stackPush()) {
			pipelineLayout = instance.pipelines.createLayout(null, "ColorLayout");

			var ciPipeline = VkGraphicsPipelineCreateInfo.calloc(stack);
			ciPipeline.sType$Default();
			ciPipeline.layout(pipelineLayout);

			var pipeline = new GraphicsPipelineBuilder(ciPipeline, instance, stack);
			pipeline.simpleShaderStages(
					"Red", "shaders/", "center.vert.spv", "red.frag.spv"
			);
			pipeline.noVertexInput();
			pipeline.simpleInputAssembly();
			ciPipeline.pTessellationState(null);
			pipeline.fixedViewport(width, height);
			pipeline.simpleRasterization(VK_CULL_MODE_NONE);
			pipeline.noMultisampling();
			pipeline.simpleColorBlending(1);
			ciPipeline.pDynamicState(null);
			pipeline.dynamicRendering(0, VK_FORMAT_UNDEFINED, VK_FORMAT_UNDEFINED, format);
			ciPipeline.basePipelineHandle(VK_NULL_HANDLE);
			ciPipeline.basePipelineIndex(0);

			graphicsPipeline = pipeline.build("RedPipeline");
		}

		var commands = new SingleTimeCommands(instance);
		commands.submit("Empty Renderpass", recorder -> {
			recorder.transitionLayout(image, null, ResourceUsage.COLOR_ATTACHMENT_WRITE);

			var colorAttachments = recorder.singleColorRenderingAttachment(
					image.vkImageView, VK_ATTACHMENT_LOAD_OP_CLEAR,
					VK_ATTACHMENT_STORE_OP_STORE, rgb(255, 0, 255)
			);
			recorder.beginSimpleDynamicRendering(width, height, colorAttachments, null, null);
			vkCmdBindPipeline(recorder.commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicsPipeline);
			vkCmdDraw(recorder.commandBuffer, 6, 1, 0, 0);
			recorder.endDynamicRendering();

			recorder.transitionLayout(image, ResourceUsage.COLOR_ATTACHMENT_WRITE, ResourceUsage.TRANSFER_SOURCE);
			recorder.copyImageToBuffer(image, destinationBuffer);
		}).awaitCompletion();
		commands.destroy();

		assertEquals((byte) 255, memGetByte(destinationBuffer.hostAddress));
		assertEquals((byte) 0, memGetByte(destinationBuffer.hostAddress + 1));
		assertEquals((byte) 255, memGetByte(destinationBuffer.hostAddress + 2));
		assertEquals((byte) 255, memGetByte(destinationBuffer.hostAddress + 3));

		long centerAddress = destinationBuffer.hostAddress + 4 * (width / 2 + width * (height / 2));
		assertEquals((byte) 255, memGetByte(centerAddress));
		assertEquals((byte) 0, memGetByte(centerAddress + 1));
		assertEquals((byte) 0, memGetByte(centerAddress + 2));
		assertEquals((byte) 255, memGetByte(centerAddress + 3));

		memory.free(instance);
		vkDestroyPipeline(instance.vkDevice(), graphicsPipeline, null);
		vkDestroyPipelineLayout(instance.vkDevice(), pipelineLayout, null);

		instance.destroyInitialObjects();
	}

	@Test
	public void testOnVk10() {
		testDynamicColorRendering(VK_API_VERSION_1_0);
	}

	@Test
	public void testOnVk11() {
		testDynamicColorRendering(VK_API_VERSION_1_1);
	}

	@Test
	public void testOnVk12() {
		testDynamicColorRendering(VK_API_VERSION_1_2);
	}

	@Test
	public void testOnVk13() {
		testDynamicColorRendering(VK_API_VERSION_1_3);
	}

	@Test
	public void testDynamicDepthAttachment() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_3, "TestDynamicDepthAttachment", 1
		).validation().forbidValidationErrors().enableDynamicRendering().build();

		int width = 20;
		int height = 30;

		var builder = new MemoryBlockBuilder(instance, "Memory");
		var image = builder.addImage(new ImageBuilder(
				"DepthImage", width, height
		).depthAttachment(VK_FORMAT_D32_SFLOAT).addUsage(VK_IMAGE_USAGE_TRANSFER_SRC_BIT));
		var destinationBuffer = builder.addMappedBuffer(4 * width * height, 4, VK_BUFFER_USAGE_TRANSFER_DST_BIT);
		var memory = builder.allocate(false);

		var commands = new SingleTimeCommands(instance);
		commands.submit("DepthCommands", recorder -> {
			recorder.transitionLayout(
					image, null,
					ResourceUsage.depthStencilAttachmentWrite(VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL)
			);

			var depthAttachment = recorder.simpleDepthRenderingAttachment(
					image.vkImageView, VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL,
					VK_ATTACHMENT_STORE_OP_STORE, 0.75f, 0
			);
			recorder.beginSimpleDynamicRendering(width, height, null, depthAttachment, null);
			recorder.endDynamicRendering();

			recorder.transitionLayout(
					image, ResourceUsage.depthStencilAttachmentWrite(VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL),
					ResourceUsage.TRANSFER_SOURCE
			);
			recorder.copyImageToBuffer(image, destinationBuffer);
		}).awaitCompletion();
		commands.destroy();

		assertEquals(0.75f, memGetFloat(destinationBuffer.hostAddress));

		memory.free(instance);
		instance.destroyInitialObjects();
	}
}
