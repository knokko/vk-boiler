package com.github.knokko.boiler.samples;

import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.builders.WindowBuilder;
import com.github.knokko.boiler.commands.CommandRecorder;
import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.pipelines.GraphicsPipelineBuilder;
import com.github.knokko.boiler.synchronization.ResourceUsage;
import com.github.knokko.boiler.window.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPushConstantRange;

import java.util.Random;

import static com.github.knokko.boiler.utilities.ColorPacker.rgb;
import static org.joml.Math.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_FIFO_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.VK_API_VERSION_1_2;

public class MultipleWindows {

	public static void main(String[] args) throws InterruptedException {
		var windows = new VkbWindow[2];

		var boiler = new BoilerBuilder(
				VK_API_VERSION_1_2, "MultipleWindowsDemo", 1
		)
				.validation()
				.enableDynamicRendering()
				.addWindow(new WindowBuilder(
						800, 500, VK_IMAGE_USAGE_TRANSFER_DST_BIT
				).callback(window -> windows[0] = window).title("FillWindow"))
				.addWindow(new WindowBuilder(
						800, 500, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT
				).callback(window -> windows[1] = window).title("SpinWindow"))
				.build();

		var eventLoop = new WindowEventLoop();
		eventLoop.addWindow(new SpinWindowLoop(windows[1]));
		eventLoop.addWindow(new FillWindowLoop(windows[0], 1f, 0f, 1f));

		//noinspection resource
		glfwSetMouseButtonCallback(windows[1].handle, (clickedWindow, button, action, modifiers) -> {
			if (action == GLFW_PRESS) startNewWindowThread(boiler, eventLoop);
		});

		eventLoop.runMain();

		boiler.destroyInitialObjects();
	}

	private static void startNewWindowThread(BoilerInstance boiler, WindowEventLoop windowLoop) {

		var rng = new Random();
		float red = rng.nextFloat();
		float green = rng.nextFloat();
		float blue = rng.nextFloat();

		String contextSuffix = String.format("Extra(%.1f, %.1f, %.1f)", red, green, blue);
		windowLoop.addWindow(new FillWindowLoop(boiler.addWindow(
				new WindowBuilder(1000, 700, VK_IMAGE_USAGE_TRANSFER_DST_BIT).title(contextSuffix).hideUntilFirstFrame()
		), red, green, blue));
	}

	private static class FillWindowLoop extends SimpleWindowRenderLoop {

		private final float red, green, blue;

		public FillWindowLoop(VkbWindow window, float red, float green, float blue) {
			super(
					window, 2, false, VK_PRESENT_MODE_FIFO_KHR,
					ResourceUsage.TRANSFER_DEST, ResourceUsage.TRANSFER_DEST
			);
			this.red = red;
			this.green = green;
			this.blue = blue;
		}

		@Override
		protected void recordFrame(
				MemoryStack stack, int frameIndex, CommandRecorder recorder,
				AcquiredImage swapchainImage, BoilerInstance boiler
		) {
			recorder.clearColorImage(swapchainImage.image().vkImage, red, green, blue, 1f);
		}
	}

	private static class SpinWindowLoop extends SimpleWindowRenderLoop {

		private long pipelineLayout, pipeline;

		public SpinWindowLoop(VkbWindow window) {
			super(
					window, 1, true, VK_PRESENT_MODE_MAILBOX_KHR,
					ResourceUsage.COLOR_ATTACHMENT_WRITE, ResourceUsage.COLOR_ATTACHMENT_WRITE
			);
		}

		@Override
		protected void setup(BoilerInstance boiler, MemoryStack stack) {
			super.setup(boiler, stack);

			var pushConstants = VkPushConstantRange.calloc(1, stack);
			pushConstants.offset(0);
			pushConstants.size(8);
			pushConstants.stageFlags(VK_SHADER_STAGE_VERTEX_BIT);

			pipelineLayout = boiler.pipelines.createLayout(pushConstants, "SpinLayout");

			var builder = new GraphicsPipelineBuilder(boiler, stack);
			builder.simpleShaderStages(
					"SpinShader", "com/github/knokko/boiler/samples/graphics/",
					"spin.vert.spv", "spin.frag.spv"
			);
			builder.noVertexInput();
			builder.simpleInputAssembly();
			builder.dynamicViewports(1);
			builder.simpleRasterization(VK_CULL_MODE_NONE);
			builder.noMultisampling();
			builder.noDepthStencil();
			builder.noColorBlending(1);
			builder.dynamicStates(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR);
			builder.ciPipeline.layout(pipelineLayout);
			builder.dynamicRendering(0, VK_FORMAT_UNDEFINED, VK_FORMAT_UNDEFINED, window.surfaceFormat);
			pipeline = builder.build("SpinPipeline");
		}

		@Override
		protected void recordFrame(
				MemoryStack stack, int frameIndex, CommandRecorder recorder,
				AcquiredImage swapchainImage, BoilerInstance boiler
		) {
			var colorAttachments = recorder.singleColorRenderingAttachment(
					swapchainImage.image().vkImageView, VK_ATTACHMENT_LOAD_OP_CLEAR,
					VK_ATTACHMENT_STORE_OP_STORE, rgb(0, 0, 200)
			);

			recorder.beginSimpleDynamicRendering(
					swapchainImage.width(), swapchainImage.height(),
					colorAttachments, null, null
			);

			recorder.dynamicViewportAndScissor(swapchainImage.width(), swapchainImage.height());
			vkCmdBindPipeline(recorder.commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);

			long periodFactor = 6_000_000L;
			long period = 360L * periodFactor;
			long progress = System.nanoTime() % period;
			float angle = toRadians(progress / (float) periodFactor);
			var pushConstants = stack.callocFloat(2);
			pushConstants.put(0, 0.8f * cos(angle));
			pushConstants.put(1, 0.8f * sin(angle));
			vkCmdPushConstants(recorder.commandBuffer, pipelineLayout, VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstants);

			vkCmdDraw(recorder.commandBuffer, 3, 1, 0, 0);

			recorder.endDynamicRendering();
		}

		@Override
		protected void cleanUp(BoilerInstance boiler) {
			super.cleanUp(boiler);
			vkDestroyPipeline(boiler.vkDevice(), pipeline, null);
			vkDestroyPipelineLayout(boiler.vkDevice(), pipelineLayout, null);
		}
	}
}
