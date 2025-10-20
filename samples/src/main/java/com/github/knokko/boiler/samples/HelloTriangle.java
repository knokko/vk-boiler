package com.github.knokko.boiler.samples;

import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.builders.WindowBuilder;
import com.github.knokko.boiler.commands.CommandRecorder;
import com.github.knokko.boiler.memory.MemoryCombiner;
import com.github.knokko.boiler.memory.callbacks.CallbackUserData;
import com.github.knokko.boiler.memory.callbacks.SumAllocationCallbacks;
import com.github.knokko.boiler.pipelines.GraphicsPipelineBuilder;
import com.github.knokko.boiler.pipelines.ShaderInfo;
import com.github.knokko.boiler.pipelines.SimpleRenderPass;
import com.github.knokko.boiler.synchronization.ResourceUsage;
import com.github.knokko.boiler.window.AcquiredImage;
import com.github.knokko.boiler.window.SwapchainResourceManager;
import com.github.knokko.boiler.synchronization.VkbFence;
import com.github.knokko.boiler.synchronization.WaitSemaphore;
import org.lwjgl.vulkan.*;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static java.lang.Thread.sleep;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.VK10.*;

public class HelloTriangle {

	public static void main(String[] args) throws InterruptedException {
		int numFramesInFlight = 3;

		var boiler = new BoilerBuilder(
				VK_API_VERSION_1_0, "HelloTriangle", VK_MAKE_VERSION(0, 1, 0)
		)
				.validation().forbidValidationErrors()
				.hideDeviceSelectionInfo()
				.allocationCallbacks(new SumAllocationCallbacks())
				.addWindow(new WindowBuilder(
						1000, 800, numFramesInFlight
				).presentModes(VK_PRESENT_MODE_FIFO_KHR, VK_PRESENT_MODE_MAILBOX_KHR))
				.build();

		var commandPool = boiler.commands.createPool(
				VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT | VK_COMMAND_POOL_CREATE_TRANSIENT_BIT,
				boiler.queueFamilies().graphics().index(), "Drawing"
		);
		var commandBuffers = boiler.commands.createPrimaryBuffers(commandPool, numFramesInFlight, "Drawing");
		var commandFences = boiler.sync.fenceBank.borrowFences(numFramesInFlight, true, "CommandFence");
		long graphicsPipeline;

		long pipelineLayout = boiler.pipelines.createLayout(null, "DrawingLayout");
		long renderPass = SimpleRenderPass.create(
				boiler, "TrianglePass", null, new SimpleRenderPass.ColorAttachment(
						boiler.window().properties.surfaceFormat(),
						VK_ATTACHMENT_LOAD_OP_CLEAR,
						VK_ATTACHMENT_STORE_OP_STORE,
						VK_SAMPLE_COUNT_1_BIT
				)
		);

		try (var stack = stackPush()) {
			var vertexModule = boiler.pipelines.createShaderModule(
					"com/github/knokko/boiler/samples/graphics/triangle.vert.spv", "TriangleVertices"
			);
			var fragmentModule = boiler.pipelines.createShaderModule(
					"com/github/knokko/boiler/samples/graphics/triangle.frag.spv", "TriangleFragments"
			);

			var vertexBindings = VkVertexInputBindingDescription.calloc(1, stack);
			vertexBindings.binding(0);
			vertexBindings.stride(4 * (2 + 3));
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
			pipelineBuilder.shaderStages(
					new ShaderInfo(VK_SHADER_STAGE_VERTEX_BIT, vertexModule, null),
					new ShaderInfo(VK_SHADER_STAGE_FRAGMENT_BIT, fragmentModule, null)
			);
			pipelineBuilder.ciPipeline.pVertexInputState(ciVertexInput);
			pipelineBuilder.simpleInputAssembly();
			pipelineBuilder.dynamicViewports(1);
			pipelineBuilder.simpleRasterization(VK_CULL_MODE_NONE);
			pipelineBuilder.noMultisampling();
			pipelineBuilder.noDepthStencil();
			pipelineBuilder.noColorBlending(1);
			pipelineBuilder.dynamicStates(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR);

			pipelineBuilder.ciPipeline.renderPass(renderPass);
			pipelineBuilder.ciPipeline.layout(pipelineLayout);

			graphicsPipeline = pipelineBuilder.build("TrianglePipeline");

			vkDestroyShaderModule(boiler.vkDevice(), vertexModule, CallbackUserData.SHADER_MODULE.put(stack, boiler));
			vkDestroyShaderModule(boiler.vkDevice(), fragmentModule, CallbackUserData.SHADER_MODULE.put(stack, boiler));
		}

		var combiner = new MemoryCombiner(boiler, "VertexMemory");
		var vertexBuffer = combiner.addMappedDeviceLocalBuffer(
				3 * Float.BYTES * (2 + 3), 24, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, 0.5f
		);
		var memory = combiner.build(false);
		var vertices = vertexBuffer.floatBuffer();
		// Put color (1, 0, 0) at position (-1, 1)
		vertices.put(-1f);
		vertices.put(1f);
		vertices.put(1f);
		vertices.put(0f);
		vertices.put(0f);
		// Put color (0, 0, 1) at position (1, 1)
		vertices.put(1f);
		vertices.put(1f);
		vertices.put(0f);
		vertices.put(0f);
		vertices.put(1f);
		// Put color (0, 1, 0) at position (0, -1)
		vertices.put(0f);
		vertices.put(-1f);
		vertices.put(0f);
		vertices.put(1f);
		vertices.put(0f);

		long frameCounter = 0;
		var swapchainResources = new SwapchainResourceManager<Object, Long>() {

			@Override
			protected Long createImage(Object swapchain, AcquiredImage swapchainImage) {
				return boiler.images.createFramebuffer(
						renderPass, swapchainImage.getWidth(), swapchainImage.getHeight(),
						"TriangleFramebuffer", swapchainImage.getImage().vkImageView
				);
			}

			@Override
			protected void destroyImage(Long framebuffer) {
				try (var stack = stackPush()) {
					vkDestroyFramebuffer(
							boiler.vkDevice(), framebuffer,
							CallbackUserData.FRAME_BUFFER.put(stack, boiler)
					);
				}
			}
		};

		long referenceTime = System.currentTimeMillis();
		long referenceFrames = 0;

		int[] pPresentMode = {VK_PRESENT_MODE_FIFO_KHR};

		//noinspection resource
		glfwSetKeyCallback(boiler.window().properties.handle(), ((window, key, scancode, action, mods) -> {
			if (action == GLFW_PRESS) {
				var spm = boiler.window().getSupportedPresentModes();
				if (key == GLFW_KEY_F) pPresentMode[0] = VK_PRESENT_MODE_FIFO_KHR;
				if (key == GLFW_KEY_M && spm.contains(VK_PRESENT_MODE_MAILBOX_KHR))
					pPresentMode[0] = VK_PRESENT_MODE_MAILBOX_KHR;
				if (key == GLFW_KEY_I && spm.contains(VK_PRESENT_MODE_IMMEDIATE_KHR))
					pPresentMode[0] = VK_PRESENT_MODE_IMMEDIATE_KHR;
				if (key == GLFW_KEY_R && spm.contains(VK_PRESENT_MODE_FIFO_RELAXED_KHR))
					pPresentMode[0] = VK_PRESENT_MODE_FIFO_RELAXED_KHR;
				if (key == GLFW_KEY_X) boiler.window().requestClose();
			}
		}));

		while (!glfwWindowShouldClose(boiler.window().properties.handle())) {
			glfwPollEvents();
			boiler.window().updateSize();

			long currentTime = System.currentTimeMillis();
			if (currentTime > 1000 + referenceTime) {
				System.out.println("FPS is " + (frameCounter - referenceFrames));
				var allocationCallbacks = (SumAllocationCallbacks) boiler.allocationCallbacks;
				System.out.println("Simple allocation callbacks: " + allocationCallbacks.copySimpleSizes());
				System.out.println("INTERNAL allocation callbacks: " + allocationCallbacks.copyInternalSizes());
				referenceTime = currentTime;
				referenceFrames = frameCounter;
			}

			try (var stack = stackPush()) {
				var swapchainImage = boiler.window().acquireSwapchainImageWithSemaphore(pPresentMode[0]);
				if (swapchainImage == null) {
					//noinspection BusyWait
					sleep(100);
					continue;
				}

				var framebuffer = swapchainResources.getImageAssociation(swapchainImage);
				WaitSemaphore[] waitSemaphores = {new WaitSemaphore(
						swapchainImage.getAcquireSemaphore(), VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
				)};

				int frameIndex = (int) (frameCounter % numFramesInFlight);
				var commandBuffer = commandBuffers[frameIndex];
				VkbFence fence = commandFences[frameIndex];
				fence.waitAndReset();

				var recorder = CommandRecorder.begin(
						commandBuffer, boiler, stack,
						VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT,
						"DrawCommands"
				);

				recorder.transitionLayout(
						swapchainImage.getImage(),
						ResourceUsage.invalidate(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT),
						ResourceUsage.COLOR_ATTACHMENT_WRITE
				);

				var pColorClear = VkClearValue.calloc(1, stack);
				pColorClear.color().float32(stack.floats(0.2f, 0.2f, 0.2f, 1f));

				var biRenderPass = VkRenderPassBeginInfo.calloc(stack);
				biRenderPass.sType$Default();
				biRenderPass.renderPass(renderPass);
				biRenderPass.framebuffer(framebuffer);
				biRenderPass.renderArea().offset().set(0, 0);
				biRenderPass.renderArea().extent().set(swapchainImage.getWidth(), swapchainImage.getHeight());
				biRenderPass.clearValueCount(1);
				biRenderPass.pClearValues(pColorClear);

				vkCmdBeginRenderPass(commandBuffer, biRenderPass, VK_SUBPASS_CONTENTS_INLINE);
				recorder.dynamicViewportAndScissor(swapchainImage.getWidth(), swapchainImage.getHeight());
				vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicsPipeline);

				recorder.bindVertexBuffers(0, vertexBuffer);

				vkCmdDraw(commandBuffer, 3, 1, 0, 0);
				vkCmdEndRenderPass(commandBuffer);
				recorder.transitionLayout(swapchainImage.getImage(), ResourceUsage.COLOR_ATTACHMENT_WRITE, ResourceUsage.PRESENT);
				assertVkSuccess(vkEndCommandBuffer(commandBuffer), "TriangleDrawing", null);

				boiler.queueFamilies().graphics().first().submit(
						commandBuffer, "SubmitDraw", waitSemaphores, fence, swapchainImage.getPresentSemaphore()
				);

				boiler.window().presentSwapchainImage(swapchainImage);
				frameCounter += 1;
			}
		}

		boiler.sync.fenceBank.awaitSubmittedFences();
		boiler.sync.fenceBank.returnFences(commandFences);

		try (var stack = stackPush()) {
			vkDestroyPipelineLayout(boiler.vkDevice(), pipelineLayout, CallbackUserData.PIPELINE_LAYOUT.put(stack, boiler));
			vkDestroyPipeline(boiler.vkDevice(), graphicsPipeline, CallbackUserData.PIPELINE.put(stack, boiler));
			vkDestroyRenderPass(boiler.vkDevice(), renderPass, CallbackUserData.RENDER_PASS.put(stack, boiler));
			vkDestroyCommandPool(boiler.vkDevice(), commandPool, CallbackUserData.COMMAND_POOL.put(stack, boiler));
		}
		memory.destroy(boiler);

		boiler.destroyInitialObjects();

		var allocationCallbacks = (SumAllocationCallbacks) boiler.allocationCallbacks;
		System.out.println("Final simple allocation callbacks: " + allocationCallbacks.copySimpleSizes());
		System.out.println("Final INTERNAL allocation callbacks: " + allocationCallbacks.copyInternalSizes());
	}
}
