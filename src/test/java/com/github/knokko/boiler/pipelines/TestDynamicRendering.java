package com.github.knokko.boiler.pipelines;

import com.github.knokko.boiler.buffer.MappedVmaBuffer;
import com.github.knokko.boiler.builder.BoilerBuilder;
import com.github.knokko.boiler.commands.CommandRecorder;
import com.github.knokko.boiler.images.VmaImage;
import com.github.knokko.boiler.sync.ResourceUsage;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkRenderingAttachmentInfo;

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
		var boiler = new BoilerBuilder(
				apiVersion, "TestDynamicColorRendering", apiVersion
		).validation().forbidValidationErrors().enableDynamicRendering().build();

		int width = 100;
		int height = 50;
		var format = VK_FORMAT_R8G8B8A8_UNORM;

		VmaImage image;
		MappedVmaBuffer destBuffer = boiler.buffers.createMapped(
				4 * width * height, VK_BUFFER_USAGE_TRANSFER_DST_BIT, "DestBuffer"
		);
		long pipelineLayout;
		long graphicsPipeline;
		try (var stack = stackPush()) {
			image = boiler.images.createSimple(
					stack, width, height, format,
					VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT,
					VK_IMAGE_ASPECT_COLOR_BIT, "TestColorAttachment"
			);

			pipelineLayout = boiler.pipelines.createLayout(stack, null, "ColorLayout");

			var ciPipeline = VkGraphicsPipelineCreateInfo.calloc(stack);
			ciPipeline.sType$Default();
			ciPipeline.layout(pipelineLayout);

			var pipeline = new GraphicsPipelineBuilder(ciPipeline, boiler, stack);
			pipeline.simpleShaderStages("Red", "shaders/center.vert.spv", "shaders/red.frag.spv");
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

		var fence = boiler.sync.createFences(false, 1, "TestFence")[0];
		var commandPool = boiler.commands.createPool(0, boiler.queueFamilies().graphics().index(), "TestPool");
		var commandBuffer = boiler.commands.createPrimaryBuffers(commandPool, 1, "TestCommandBuffer")[0];

		try (var stack = stackPush()) {
			var recorder = CommandRecorder.begin(commandBuffer, boiler, stack, "Empty RenderPass");

			recorder.transitionColorLayout(
					image.vkImage(), VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, null,
					new ResourceUsage(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
			);

			var colorAttachments = VkRenderingAttachmentInfo.calloc(1, stack);
			recorder.simpleColorRenderingAttachment(
					colorAttachments.get(0), image.vkImageView(),
					VK_ATTACHMENT_LOAD_OP_CLEAR, VK_ATTACHMENT_STORE_OP_STORE,
					1f, 0f, 1f, 1f
			);
			recorder.beginSimpleDynamicRendering(width, height, colorAttachments, null, null);

			vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicsPipeline);
			vkCmdDraw(commandBuffer, 6, 1, 0, 0);

			recorder.endDynamicRendering();

			recorder.transitionColorLayout(
					image.vkImage(), VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
					new ResourceUsage(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT),
					new ResourceUsage(VK_ACCESS_TRANSFER_READ_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT)
			);

			recorder.copyImageToBuffer(VK_IMAGE_ASPECT_COLOR_BIT, image.vkImage(), width, height, destBuffer.vkBuffer());

			recorder.end();
			boiler.queueFamilies().graphics().queues().get(0).submit(commandBuffer, "Test", null, fence);
			boiler.sync.waitAndReset(stack, fence);

			assertEquals((byte) 255, memGetByte(destBuffer.hostAddress()));
			assertEquals((byte) 0, memGetByte(destBuffer.hostAddress() + 1));
			assertEquals((byte) 255, memGetByte(destBuffer.hostAddress() + 2));
			assertEquals((byte) 255, memGetByte(destBuffer.hostAddress() + 3));

			long centerAddress = destBuffer.hostAddress() + 4 * (width / 2 + width * (height / 2));
			assertEquals((byte) 255, memGetByte(centerAddress));
			assertEquals((byte) 0, memGetByte(centerAddress + 1));
			assertEquals((byte) 0, memGetByte(centerAddress + 2));
			assertEquals((byte) 255, memGetByte(centerAddress + 3));
		}

		image.destroy(boiler);
		destBuffer.destroy(boiler.vmaAllocator());
		vkDestroyPipeline(boiler.vkDevice(), graphicsPipeline, null);
		vkDestroyPipelineLayout(boiler.vkDevice(), pipelineLayout, null);
		vkDestroyFence(boiler.vkDevice(), fence, null);
		vkDestroyCommandPool(boiler.vkDevice(), commandPool, null);

		boiler.destroyInitialObjects();
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
		var boiler = new BoilerBuilder(
				VK_API_VERSION_1_3, "TestDynamicDepthAttachment", 1
		).validation().forbidValidationErrors().enableDynamicRendering().build();

		int width = 20;
		int height = 30;

		VmaImage image;
		try (var stack = stackPush()) {
			image = boiler.images.createSimple(
					stack, width, height, VK_FORMAT_D32_SFLOAT,
					VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT,
					VK_IMAGE_ASPECT_DEPTH_BIT, "DepthImage"
			);
		}

		var destBuffer = boiler.buffers.createMapped(
				4 * width * height, VK_BUFFER_USAGE_TRANSFER_DST_BIT, "DestBuffer"
		);

		var commandPool = boiler.commands.createPool(0, boiler.queueFamilies().graphics().index(), "DepthPool");
		var commandBuffer = boiler.commands.createPrimaryBuffers(commandPool, 1, "DepthCommandBuffer")[0];
		var fence = boiler.sync.createFences(false, 1, "DepthFence")[0];

		try (var stack = stackPush()) {
			var recorder = CommandRecorder.begin(commandBuffer, boiler, stack, "DepthCommands");

			recorder.transitionDepthLayout(
					image.vkImage(), VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL, null,
					new ResourceUsage(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT, VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT)
			);

			var depthAttachment = recorder.simpleDepthRenderingAttachment(
					stack, image.vkImageView(), VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL,
					VK_ATTACHMENT_STORE_OP_STORE, 0.75f, 0
			);

			recorder.beginSimpleDynamicRendering(width, height, null, depthAttachment, null);

			recorder.endDynamicRendering();

			recorder.transitionDepthLayout(
					image.vkImage(), VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
					new ResourceUsage(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT, VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT),
					new ResourceUsage(VK_ACCESS_TRANSFER_READ_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT)
			);

			recorder.copyImageToBuffer(VK_IMAGE_ASPECT_DEPTH_BIT, image.vkImage(), width, height, destBuffer.vkBuffer());

			recorder.end();
			boiler.queueFamilies().graphics().queues().get(0).submit(
					commandBuffer, "DepthSubmission", null, fence
			);
			boiler.sync.waitAndReset(stack, fence);

			assertEquals(0.75f, memGetFloat(destBuffer.hostAddress()));
		}

		vkDestroyFence(boiler.vkDevice(), fence, null);
		destBuffer.destroy(boiler.vmaAllocator());
		image.destroy(boiler);
		vkDestroyCommandPool(boiler.vkDevice(), commandPool, null);
		boiler.destroyInitialObjects();
	}
}