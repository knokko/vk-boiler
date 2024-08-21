package com.github.knokko.boiler.samples;

import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.builders.WindowBuilder;
import com.github.knokko.boiler.commands.CommandRecorder;
import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.pipelines.GraphicsPipelineBuilder;
import com.github.knokko.boiler.synchronization.AwaitableSubmission;
import com.github.knokko.boiler.synchronization.VkbFence;
import com.github.knokko.boiler.window.AcquiredImage;
import com.github.knokko.boiler.window.SwapchainResourceManager;
import com.github.knokko.boiler.synchronization.ResourceUsage;
import com.github.knokko.boiler.synchronization.WaitSemaphore;
import com.github.knokko.boiler.window.VkbWindow;
import com.github.knokko.boiler.window.WindowRenderLoop;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.VK_API_VERSION_1_1;

public class SimpleRingApproximation extends WindowRenderLoop {

	public static void main(String[] args) throws InterruptedException {
		var boiler = new BoilerBuilder(
				VK_API_VERSION_1_1, "SimpleRingApproximation", VK_MAKE_VERSION(0, 2, 0)
		)
				.validation()
				.enableDynamicRendering()
				.addWindow(new WindowBuilder(1000, 8000, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT))
				.build();
		new SimpleRingApproximation(boiler.window()).start();
		boiler.destroyInitialObjects();
	}

	private long commandPool, graphicsPipeline, pipelineLayout;
	private VkCommandBuffer[] commandBuffers;
	private VkbFence[] commandFences;
	private SwapchainResourceManager<Long> swapchainResources;

	public SimpleRingApproximation(VkbWindow window) {
		super(window, 3, false, VK_PRESENT_MODE_MAILBOX_KHR);
	}

	@Override
	protected void setup(BoilerInstance boiler) {
		commandPool = boiler.commands.createPool(
				VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT,
				boiler.queueFamilies().graphics().index(),
				"Drawing"
		);
		commandBuffers = boiler.commands.createPrimaryBuffers(commandPool, numFramesInFlight, "Drawing");
		commandFences = boiler.sync.fenceBank.borrowFences(numFramesInFlight, true, "Fence");

		try (var stack = stackPush()) {
			var pushConstants = VkPushConstantRange.calloc(1, stack);
			pushConstants.stageFlags(VK_SHADER_STAGE_VERTEX_BIT);
			pushConstants.offset(0);
			pushConstants.size(20);

			pipelineLayout = boiler.pipelines.createLayout(stack, pushConstants, "DrawingLayout");

			var pipelineBuilder = new GraphicsPipelineBuilder(boiler, stack);
			pipelineBuilder.simpleShaderStages(
					"Ring", "com/github/knokko/boiler/samples/graphics/ring.vert.spv",
					"com/github/knokko/boiler/samples/graphics/ring.frag.spv"
			);
			pipelineBuilder.noVertexInput();
			pipelineBuilder.simpleInputAssembly();
			pipelineBuilder.dynamicViewports(1);
			pipelineBuilder.simpleRasterization(VK_CULL_MODE_NONE);
			pipelineBuilder.noMultisampling();
			pipelineBuilder.noDepthStencil();
			pipelineBuilder.noColorBlending(1);
			pipelineBuilder.dynamicStates(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR);
			pipelineBuilder.dynamicRendering(0, VK_FORMAT_UNDEFINED, VK_FORMAT_UNDEFINED, boiler.window().surfaceFormat);
			pipelineBuilder.ciPipeline.layout(pipelineLayout);

			graphicsPipeline = pipelineBuilder.build("RingApproximation");
		}

		swapchainResources = new SwapchainResourceManager<>(swapchainImage -> boiler.images.createSimpleView(
				swapchainImage.vkImage(), boiler.window().surfaceFormat,
				VK_IMAGE_ASPECT_COLOR_BIT, "SwapchainView" + swapchainImage.index()
		), imageView -> vkDestroyImageView(boiler.vkDevice(), imageView, null));
	}

	@Override
	protected AwaitableSubmission renderFrame(
			MemoryStack stack, int frameIndex, AcquiredImage swapchainImage, BoilerInstance boiler
	) {
		var swapchainImageView = swapchainResources.get(swapchainImage);
		WaitSemaphore[] waitSemaphores = {new WaitSemaphore(
				swapchainImage.acquireSemaphore(), VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
		)};

		var commandBuffer = commandBuffers[frameIndex];
		var fence = commandFences[frameIndex];
		fence.waitAndReset();

		var recorder = CommandRecorder.begin(commandBuffer, boiler, stack, "RingApproximation");

		recorder.transitionColorLayout(
				swapchainImage.vkImage(),
				ResourceUsage.fromPresent(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT),
				ResourceUsage.COLOR_ATTACHMENT_WRITE
		);

		var colorAttachments = VkRenderingAttachmentInfo.calloc(1, stack);
		recorder.simpleColorRenderingAttachment(
				colorAttachments.get(0), swapchainImageView,
				VK_ATTACHMENT_LOAD_OP_CLEAR, VK_ATTACHMENT_STORE_OP_STORE,
				0.07f, 0.4f, 0.6f, 1f
		);

		recorder.beginSimpleDynamicRendering(
				swapchainImage.width(), swapchainImage.height(),
				colorAttachments, null, null
		);

		recorder.dynamicViewportAndScissor(swapchainImage.width(), swapchainImage.height());
		vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicsPipeline);

		int numTriangles = 30_000_000;
		var pushConstants = stack.calloc(20);
		pushConstants.putFloat(0, 0.6f);
		pushConstants.putFloat(4, 0.8f);
		pushConstants.putFloat(8, 0.2f);
		pushConstants.putFloat(12, -0.1f);
		pushConstants.putInt(16, 2 * numTriangles);

		vkCmdPushConstants(commandBuffer, pipelineLayout, VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstants);

		vkCmdDraw(commandBuffer, 6 * numTriangles, 1, 0, 0);
		recorder.endDynamicRendering();

		recorder.transitionColorLayout(
				swapchainImage.vkImage(), ResourceUsage.COLOR_ATTACHMENT_WRITE, ResourceUsage.PRESENT
		);

		recorder.end();

		return boiler.queueFamilies().graphics().first().submit(
				commandBuffer, "RingApproximation", waitSemaphores, fence, swapchainImage.presentSemaphore()
		);
	}

	@Override
	protected void cleanUp(BoilerInstance boiler) {
		for (var fence : commandFences) fence.waitIfSubmitted();
		boiler.sync.fenceBank.returnFences(commandFences);

		vkDestroyPipelineLayout(boiler.vkDevice(), pipelineLayout, null);
		vkDestroyPipeline(boiler.vkDevice(), graphicsPipeline, null);
		vkDestroyCommandPool(boiler.vkDevice(), commandPool, null);
	}
}
