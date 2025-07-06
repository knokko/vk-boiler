package com.github.knokko.boiler.samples;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.buffers.MappedVkbBuffer;
import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.builders.WindowBuilder;
import com.github.knokko.boiler.commands.CommandRecorder;
import com.github.knokko.boiler.memory.MemoryBlock;
import com.github.knokko.boiler.memory.MemoryCombiner;
import com.github.knokko.boiler.pipelines.GraphicsPipelineBuilder;
import com.github.knokko.boiler.synchronization.ResourceUsage;
import com.github.knokko.boiler.window.AcquiredImage;
import com.github.knokko.boiler.window.SimpleWindowRenderLoop;
import com.github.knokko.boiler.window.VkbWindow;
import com.github.knokko.boiler.window.WindowEventLoop;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;

import static com.github.knokko.boiler.utilities.ColorPacker.rgb;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_FIFO_KHR;
import static org.lwjgl.vulkan.VK10.*;

public class MiniTriangle extends SimpleWindowRenderLoop {

	private MemoryBlock memory;
	private MappedVkbBuffer vertexBuffer;
	private long pipelineLayout, graphicsPipeline;

	public MiniTriangle(VkbWindow window) {
		super(
				window, 2, false, VK_PRESENT_MODE_FIFO_KHR,
				ResourceUsage.COLOR_ATTACHMENT_WRITE, ResourceUsage.COLOR_ATTACHMENT_WRITE
		);
	}

	@Override
	protected void setup(BoilerInstance boiler, MemoryStack stack) {
		super.setup(boiler, stack);
		var combiner = new MemoryCombiner(boiler, "VertexMemory");
		this.vertexBuffer = combiner.addMappedDeviceLocalBuffer(
				3 * Float.BYTES * (2 + 3), 24, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, 0.5f
		);
		this.memory = combiner.build(false);

		var vertices = vertexBuffer.floatBuffer();
		// Put color (1, 0, 0) at position (-1, 1)
		vertices.put(-1f).put(1f);
		vertices.put(1f).put(0f).put(0f);
		// Put color (0, 0, 1) at position (1, 1)
		vertices.put(1f).put(1f);
		vertices.put(0f).put(0f).put(1);
		// Put color (0, 1, 0) at position (0, -1)
		vertices.put(0f).put(-1f);
		vertices.put(0f).put(1f).put(0f);

		this.pipelineLayout = boiler.pipelines.createLayout(null, "DrawingLayout");
		var vertexBindings = VkVertexInputBindingDescription.calloc(1, stack);
		vertexBindings.binding(0);
		vertexBindings.stride(Float.BYTES * (2 + 3));
		vertexBindings.inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

		var vertexAttributes = VkVertexInputAttributeDescription.calloc(2, stack);
		var attributePosition = vertexAttributes.get(0);
		attributePosition.location(0);
		attributePosition.binding(0);
		attributePosition.format(VK_FORMAT_R32G32_SFLOAT);
		attributePosition.offset(0);
		var attributeColor = vertexAttributes.get(1);
		attributeColor.location(1);
		attributeColor.binding(0);
		attributeColor.format(VK_FORMAT_R32G32B32_SFLOAT);
		attributeColor.offset(4 * 2);

		var ciVertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack);
		ciVertexInput.sType$Default();
		ciVertexInput.pVertexBindingDescriptions(vertexBindings);
		ciVertexInput.pVertexAttributeDescriptions(vertexAttributes);

		var pipelineBuilder = new GraphicsPipelineBuilder(boiler, stack);
		pipelineBuilder.simpleShaderStages(
				"Triangle", "com/github/knokko/boiler/samples/graphics/",
				"triangle.vert.spv", "triangle.frag.spv"
		);
		pipelineBuilder.ciPipeline.pVertexInputState(ciVertexInput);
		pipelineBuilder.simpleInputAssembly();
		pipelineBuilder.dynamicViewports(1);
		pipelineBuilder.simpleRasterization(VK_CULL_MODE_NONE);
		pipelineBuilder.noMultisampling();
		pipelineBuilder.noDepthStencil();
		pipelineBuilder.noColorBlending(1);
		pipelineBuilder.dynamicStates(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR);
		pipelineBuilder.dynamicRendering(0, VK_FORMAT_UNDEFINED, VK_FORMAT_UNDEFINED, window.surfaceFormat);
		pipelineBuilder.ciPipeline.layout(pipelineLayout);
		this.graphicsPipeline = pipelineBuilder.build("TrianglePipeline");
	}

	@Override
	protected void recordFrame(MemoryStack stack, int frameIndex, CommandRecorder recorder, AcquiredImage acquiredImage, BoilerInstance instance) {
		var colorAttachments = recorder.singleColorRenderingAttachment(
				acquiredImage.image().vkImageView, VK_ATTACHMENT_LOAD_OP_CLEAR,
				VK_ATTACHMENT_STORE_OP_STORE, rgb(20, 120, 180)
		);

		recorder.beginSimpleDynamicRendering(
				acquiredImage.width(), acquiredImage.height(),
				colorAttachments, null, null
		);
		recorder.dynamicViewportAndScissor(acquiredImage.width(), acquiredImage.height());
		vkCmdBindPipeline(recorder.commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicsPipeline);
		recorder.bindVertexBuffers(0, vertexBuffer);
		vkCmdDraw(recorder.commandBuffer, 3, 1, 0, 0);
		recorder.endDynamicRendering();
	}

	@Override
	protected void cleanUp(BoilerInstance boiler) {
		super.cleanUp(boiler);
		memory.destroy(boiler);
		vkDestroyPipeline(boiler.vkDevice(), graphicsPipeline, null);
		vkDestroyPipelineLayout(boiler.vkDevice(), pipelineLayout, null);
	}

	public static void main(String[] args) {
		var boiler = new BoilerBuilder(
				VK_API_VERSION_1_0, "MiniTriangle", VK_MAKE_VERSION(0, 1, 0)
		)
				.validation().forbidValidationErrors()
				.addWindow(new WindowBuilder(1000, 800, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT))
				.enableDynamicRendering()
				.build();

		var eventLoop = new WindowEventLoop();
		eventLoop.addWindow(new MiniTriangle(boiler.window()));
		eventLoop.runMain();

		boiler.destroyInitialObjects();
	}
}
