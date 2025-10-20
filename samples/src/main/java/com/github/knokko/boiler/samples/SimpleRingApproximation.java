package com.github.knokko.boiler.samples;

import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.builders.WindowBuilder;
import com.github.knokko.boiler.builders.instance.ValidationFeatures;
import com.github.knokko.boiler.commands.CommandRecorder;
import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.pipelines.GraphicsPipelineBuilder;
import com.github.knokko.boiler.synchronization.VkbFence;
import com.github.knokko.boiler.synchronization.ResourceUsage;
import com.github.knokko.boiler.synchronization.WaitSemaphore;
import com.github.knokko.boiler.window.AcquiredImage;
import com.github.knokko.boiler.window.VkbWindow;
import com.github.knokko.boiler.window.WindowRenderLoop;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static com.github.knokko.boiler.utilities.ColorPacker.rgb;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_FIFO_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.VK_API_VERSION_1_1;

/**
 * This sample uses an insane amount of triangles to approximate the shape of a yellow ring. I would not recommend
 * running this sample on laptops or desktops with weak graphics cards.
 */
public class SimpleRingApproximation extends WindowRenderLoop {

	public static void main(String[] args) throws InterruptedException {
		var boiler = new BoilerBuilder(
				VK_API_VERSION_1_1, "SimpleRingApproximation", VK_MAKE_VERSION(0, 2, 0)
		)
				.validation(new ValidationFeatures(false, false, true, true))
				.enableDynamicRendering()
				.addWindow(new WindowBuilder(1000, 8000, 3).hideFirstFrames(5))
				.build();
		new SimpleRingApproximation(boiler.window()).start();
		boiler.destroyInitialObjects();
	}

	private long commandPool, graphicsPipeline, pipelineLayout;
	private VkCommandBuffer[] commandBuffers;
	private VkbFence[] commandFences;

	public SimpleRingApproximation(VkbWindow window) {
		super(window, false,
				window.getSupportedPresentModes().contains(VK_PRESENT_MODE_MAILBOX_KHR) ?
						VK_PRESENT_MODE_MAILBOX_KHR : VK_PRESENT_MODE_FIFO_KHR
		);
	}

	@Override
	protected void setup(BoilerInstance boiler, MemoryStack stack) {
		commandPool = boiler.commands.createPool(
				VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT,
				boiler.queueFamilies().graphics().index(),
				"Drawing"
		);
		commandBuffers = boiler.commands.createPrimaryBuffers(commandPool, numFramesInFlight, "Drawing");
		commandFences = boiler.sync.fenceBank.borrowFences(numFramesInFlight, true, "Fence");

		var pushConstants = VkPushConstantRange.calloc(1, stack);
		pushConstants.stageFlags(VK_SHADER_STAGE_VERTEX_BIT);
		pushConstants.offset(0);
		pushConstants.size(20);

		pipelineLayout = boiler.pipelines.createLayout(pushConstants, "DrawingLayout");

		var pipelineBuilder = new GraphicsPipelineBuilder(boiler, stack);
		pipelineBuilder.simpleShaderStages(
				"Ring", "com/github/knokko/boiler/samples/graphics/",
				"ring.vert.spv", "ring.frag.spv"
		);
		pipelineBuilder.noVertexInput();
		pipelineBuilder.simpleInputAssembly();
		pipelineBuilder.dynamicViewports(1);
		pipelineBuilder.simpleRasterization(VK_CULL_MODE_NONE);
		pipelineBuilder.noMultisampling();
		pipelineBuilder.noDepthStencil();
		pipelineBuilder.noColorBlending(1);
		pipelineBuilder.dynamicStates(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR);
		pipelineBuilder.dynamicRendering(
				0, VK_FORMAT_UNDEFINED, VK_FORMAT_UNDEFINED,
				boiler.window().properties.surfaceFormat()
		);
		pipelineBuilder.ciPipeline.layout(pipelineLayout);
		graphicsPipeline = pipelineBuilder.build("RingApproximation");
	}

	@Override
	protected void renderFrame(
			MemoryStack stack, int frameIndex, AcquiredImage swapchainImage, BoilerInstance boiler
	) {
		WaitSemaphore[] waitSemaphores = {new WaitSemaphore(
				swapchainImage.getAcquireSemaphore(), VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
		)};

		var commandBuffer = commandBuffers[frameIndex];
		var fence = commandFences[frameIndex];
		fence.waitAndReset();

		var recorder = CommandRecorder.begin(commandBuffer, boiler, stack, "RingApproximation");

		recorder.transitionLayout(
				swapchainImage.getImage(),
				ResourceUsage.invalidate(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT),
				ResourceUsage.COLOR_ATTACHMENT_WRITE
		);

		var colorAttachments = recorder.singleColorRenderingAttachment(
				swapchainImage.getImage().vkImageView, VK_ATTACHMENT_LOAD_OP_CLEAR,
				VK_ATTACHMENT_STORE_OP_STORE, rgb(20, 120, 180)
		);

		recorder.beginSimpleDynamicRendering(
				swapchainImage.getWidth(), swapchainImage.getHeight(),
				colorAttachments, null, null
		);

		recorder.dynamicViewportAndScissor(swapchainImage.getWidth(), swapchainImage.getHeight());
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

		recorder.transitionLayout(swapchainImage.getImage(), ResourceUsage.COLOR_ATTACHMENT_WRITE, ResourceUsage.PRESENT);

		recorder.end();

		boiler.queueFamilies().graphics().first().submit(
				commandBuffer, "RingApproximation", waitSemaphores, fence, swapchainImage.getPresentSemaphore()
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
