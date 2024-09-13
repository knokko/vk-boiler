package com.github.knokko.boiler.samples;

import com.github.knokko.boiler.buffers.MappedVkbBuffer;
import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.builders.xr.BoilerXrBuilder;
import com.github.knokko.boiler.commands.CommandRecorder;
import com.github.knokko.boiler.descriptors.VkbDescriptorSetLayout;
import com.github.knokko.boiler.descriptors.HomogeneousDescriptorPool;
import com.github.knokko.boiler.pipelines.GraphicsPipelineBuilder;
import com.github.knokko.boiler.xr.SuggestedBindingsBuilder;
import com.github.knokko.boiler.xr.VkbSession;
import com.github.knokko.boiler.xr.SessionLoop;
import org.joml.Matrix4f;
import org.lwjgl.openxr.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static com.github.knokko.boiler.exceptions.OpenXrFailureException.assertXrSuccess;
import static org.lwjgl.openxr.XR10.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memFloatBuffer;
import static org.lwjgl.system.MemoryUtil.memIntBuffer;
import static org.lwjgl.util.vma.Vma.vmaDestroyImage;
import static org.lwjgl.vulkan.KHRDynamicRendering.*;
import static org.lwjgl.vulkan.KHRGetPhysicalDeviceProperties2.vkGetPhysicalDeviceFeatures2KHR;
import static org.lwjgl.vulkan.KHRMultiview.VK_KHR_MULTIVIEW_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK13.*;

public class HelloXR {

	private static final int NUM_FRAMES_IN_FLIGHT = 2;

	public static void main(String[] args) throws InterruptedException {
		var boiler = new BoilerBuilder(
				VK_API_VERSION_1_0, "HelloXR", 1
		)
				.validation()
				.enableDynamicRendering()
				.requiredDeviceExtensions(VK_KHR_MULTIVIEW_EXTENSION_NAME)
				.printDeviceRejectionInfo()
				.extraDeviceRequirements((physicalDevice, windowSurface, stack) -> {
					var multiview = VkPhysicalDeviceMultiviewFeaturesKHR.calloc(stack);
					multiview.sType$Default();

					var features2 = VkPhysicalDeviceFeatures2KHR.calloc(stack);
					features2.sType$Default();
					features2.pNext(multiview);

					vkGetPhysicalDeviceFeatures2KHR(physicalDevice, features2);
					return multiview.multiview();
				})
				.beforeDeviceCreation((ciDevice, instanceExtensions, physicalDevice, stack) -> {
					var multiview = VkPhysicalDeviceMultiviewFeaturesKHR.calloc(stack);
					multiview.sType$Default();
					multiview.multiview(true);

					ciDevice.pNext(multiview);
				})
				.xr(new BoilerXrBuilder())
				.build();

		var session = boiler.xr().createSession(0, null);

		int swapchainFormat = session.chooseSwapchainFormat(
				VK_FORMAT_R8G8B8_SRGB, VK_FORMAT_B8G8R8_SRGB,
				VK_FORMAT_R8G8B8A8_SRGB, VK_FORMAT_B8G8R8A8_SRGB
		);
		int depthFormat = boiler.images.chooseDepthStencilFormat(
				VK_FORMAT_X8_D24_UNORM_PACK32, VK_FORMAT_D24_UNORM_S8_UINT, VK_FORMAT_D32_SFLOAT
		);

		int width, height;
		try (var stack = stackPush()) {
			var views = boiler.xr().getViewConfigurationViews(stack, XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO, true);
			if (views.capacity() != 2)
				throw new UnsupportedOperationException("Expected 2 views, but got " + views.capacity());

			width = views.recommendedImageRectWidth();
			height = views.recommendedImageRectHeight();
		}

		var swapchain = session.createSwapchain(
				2, width, height, swapchainFormat, XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT
		);

		long[] swapchainImages = boiler.xr().getSwapchainImages(swapchain);
		long[] swapchainImageViews = new long[swapchainImages.length];
		for (int index = 0; index < swapchainImages.length; index++) {
			swapchainImageViews[index] = boiler.images.createView(
					swapchainImages[index], swapchainFormat,
					VK_IMAGE_ASPECT_COLOR_BIT, 1, 2, "SwapchainView"
			);
		}
		var depthImage = boiler.images.create(
				width, height, depthFormat, VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT,
				VK_IMAGE_ASPECT_DEPTH_BIT, VK_SAMPLE_COUNT_1_BIT, 1, 2, true, "DepthImage"
		);

		var commandPools = boiler.commands.createPools(
				VK_COMMAND_POOL_CREATE_TRANSIENT_BIT, boiler.queueFamilies().graphics().index(),
				NUM_FRAMES_IN_FLIGHT, "Drawing"
		);
		var commandBuffers = boiler.commands.createPrimaryBufferPerPool("Drawing", commandPools);
		var fences = boiler.sync.fenceBank.borrowFences(NUM_FRAMES_IN_FLIGHT, false, "Drawing");

		int vertexSize = (3 + 3) * 4;
		var vertexBuffer = boiler.buffers.createMapped(
				4 * vertexSize, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, "VertexBuffer"
		);
		var hostVertexBuffer = memFloatBuffer(vertexBuffer.hostAddress(), 4 * 6);
		hostVertexBuffer.put(-1f).put(0f).put(1f); // vertex1.position = (-1, 0, 1)
		hostVertexBuffer.put(1f).put(0f).put(0f); // vertex1.color = red
		hostVertexBuffer.put(1f).put(0f).put(1f); // vertex2.position = (1, 0, 1)
		hostVertexBuffer.put(0f).put(1f).put(0f); // vertex2.color = green
		hostVertexBuffer.put(0f).put(0f).put(-1f); // vertex3.position = (0, 0, -1)
		hostVertexBuffer.put(0f).put(0f).put(1f); // vertex3.color = blue
		hostVertexBuffer.put(0f).put(1f).put(0f); // vertex4.position = (0, 1, 0)
		hostVertexBuffer.put(0.5f).put(0.5f).put(0.5f); // vertex4.color = grey

		var indexBuffer = boiler.buffers.createMapped(
				5 * 3 * 4, VK_BUFFER_USAGE_INDEX_BUFFER_BIT, "IndexBuffer"
		);
		var hostIndexBuffer = memIntBuffer(indexBuffer.hostAddress(), 5 * 3);
		hostIndexBuffer.put(0).put(1).put(2); // bottom triangle, pointing up
		hostIndexBuffer.put(2).put(1).put(0); // bottom triangle, pointing down
		hostIndexBuffer.put(0).put(1).put(3); // back of the hand triangle
		hostIndexBuffer.put(1).put(2).put(3); // right of the hand triangle
		hostIndexBuffer.put(2).put(0).put(3); // left of the hand triangle

		var matrixBuffers = new MappedVkbBuffer[NUM_FRAMES_IN_FLIGHT];
		for (int index = 0; index < NUM_FRAMES_IN_FLIGHT; index++) {
			matrixBuffers[index] = boiler.buffers.createMapped(
					5 * 64, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, "MatrixBuffer"
			);
		}

		VkbDescriptorSetLayout descriptorSetLayout;
		HomogeneousDescriptorPool descriptorPool;
		long[] descriptorSets;
		long pipelineLayout;
		long graphicsPipeline;
		try (var stack = stackPush()) {

			var layoutBindings = VkDescriptorSetLayoutBinding.calloc(1, stack);
			boiler.descriptors.binding(layoutBindings, 0, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_SHADER_STAGE_VERTEX_BIT);

			descriptorSetLayout = boiler.descriptors.createLayout(stack, layoutBindings, "MatricesLayout");
			descriptorPool = descriptorSetLayout.createPool(NUM_FRAMES_IN_FLIGHT, 0, "MatricesPool");
			descriptorSets = descriptorPool.allocate(NUM_FRAMES_IN_FLIGHT);

			var descriptorWrites = VkWriteDescriptorSet.calloc(1, stack);
			for (int index = 0; index < NUM_FRAMES_IN_FLIGHT; index++) {
				boiler.descriptors.writeBuffer(
						stack, descriptorWrites, descriptorSets[index],
						0, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, matrixBuffers[index].fullRange()
				);
				vkUpdateDescriptorSets(boiler.vkDevice(), descriptorWrites, null);
			}

			var pushConstants = VkPushConstantRange.calloc(1, stack);
			pushConstants.stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT);
			pushConstants.offset(0);
			pushConstants.size(8);

			pipelineLayout = boiler.pipelines.createLayout(
					pushConstants, "SimplePipelineLayout", descriptorSetLayout.vkDescriptorSetLayout
			);

			var vertexBindings = VkVertexInputBindingDescription.calloc(1, stack);
			vertexBindings.binding(0);
			vertexBindings.stride(vertexSize);
			vertexBindings.inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

			var vertexAttributes = VkVertexInputAttributeDescription.calloc(2, stack);
			var positionAttribute = vertexAttributes.get(0);
			positionAttribute.location(0);
			positionAttribute.binding(0);
			positionAttribute.format(VK_FORMAT_R32G32B32_SFLOAT);
			positionAttribute.offset(0);
			var colorAttribute = vertexAttributes.get(1);
			colorAttribute.location(1);
			colorAttribute.binding(0);
			colorAttribute.format(VK_FORMAT_R32G32B32_SFLOAT);
			colorAttribute.offset(12);

			var ciVertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack);
			ciVertexInput.sType$Default();
			ciVertexInput.pVertexBindingDescriptions(vertexBindings);
			ciVertexInput.pVertexAttributeDescriptions(vertexAttributes);

			var pipelineBuilder = new GraphicsPipelineBuilder(boiler, stack);
			pipelineBuilder.simpleShaderStages(
					"Xr", "com/github/knokko/boiler/samples/graphics/xr.vert.spv",
					"com/github/knokko/boiler/samples/graphics/xr.frag.spv"
			);
			pipelineBuilder.ciPipeline.pVertexInputState(ciVertexInput);
			pipelineBuilder.simpleInputAssembly();
			pipelineBuilder.fixedViewport(width, height);
			pipelineBuilder.simpleRasterization(VK_CULL_MODE_BACK_BIT);
			pipelineBuilder.noMultisampling();
			pipelineBuilder.simpleDepth(VK_COMPARE_OP_LESS_OR_EQUAL);
			pipelineBuilder.ciPipeline.layout(pipelineLayout);
			pipelineBuilder.noColorBlending(1);
			pipelineBuilder.dynamicRendering(3, depthFormat, VK_FORMAT_UNDEFINED, swapchainFormat);

			graphicsPipeline = pipelineBuilder.build("SimplePipeline");
		}

		XrActionSet actionSet;
		XrAction handPoseAction, handClickAction, scaleAction;
		long pathLeftHand, pathRightHand;
		XrSpace leftHandSpace, rightHandSpace, renderSpace;
		try (var stack = stackPush()) {
			actionSet = boiler.xr().actions.createSet(
					stack, 1, "hands",
					"Hand poses and click information"
			);
			pathLeftHand = boiler.xr().actions.getPath(stack, "/user/hand/left");
			pathRightHand = boiler.xr().actions.getPath(stack, "/user/hand/right");

			handPoseAction = boiler.xr().actions.createWithSubactions(
					stack, actionSet, "hand_poses", "Hand poses",
					XR_ACTION_TYPE_POSE_INPUT, pathLeftHand, pathRightHand
			);
			handClickAction = boiler.xr().actions.createWithSubactions(
					stack, actionSet, "hand_clicks", "Clicking with hands",
					XR_ACTION_TYPE_BOOLEAN_INPUT, pathLeftHand, pathRightHand
			);
			scaleAction = boiler.xr().actions.create(
					stack, actionSet, "scale_floor_triangle", "Scale floor triangle",
					XR_ACTION_TYPE_FLOAT_INPUT
			);

			var bindingsBuilder = new SuggestedBindingsBuilder(boiler.xr(), "/interaction_profiles/khr/simple_controller");
			bindingsBuilder.add(handPoseAction, "/user/hand/left/input/aim/pose");
			bindingsBuilder.add(handPoseAction, "/user/hand/right/input/aim/pose");
			bindingsBuilder.add(handClickAction, "/user/hand/left/input/select/click");
			bindingsBuilder.add(handClickAction, "/user/hand/right/input/select/click");
			bindingsBuilder.add(scaleAction, "/user/hand/left/input/menu/click");
			bindingsBuilder.finish();

			leftHandSpace = session.createActionSpace(stack, handPoseAction, pathLeftHand, "left hand");
			rightHandSpace = session.createActionSpace(stack, handPoseAction, pathRightHand, "right hand");
			renderSpace = session.createReferenceSpace(stack, XR_REFERENCE_SPACE_TYPE_STAGE);

			session.attach(stack, actionSet);
		}

		// Stop demo after 10 seconds
		long endTime = System.nanoTime() + 10_000_000_000L;

		class HelloSessionLoop extends SessionLoop {

			private int frameIndex;

			public HelloSessionLoop(
					VkbSession session, XrSpace renderSpace,
					XrSwapchain swapchain, int width, int height
			) {
				super(session, renderSpace, swapchain, width, height);
			}

			@Override
			protected Matrix4f createProjectionMatrix(XrFovf fov) {
				return xr.createProjectionMatrix(fov, 0.01f, 100f);
			}

			@Override
			protected XrActionSet[] chooseActiveActionSets() {
				return new XrActionSet[]{actionSet};
			}

			@Override
			protected void update() {
				if (System.nanoTime() > endTime) requestExit();
			}

			@Override
			protected void handleEvent(XrEventDataBuffer event) {
				System.out.println("Handled event of type " + event.type() + ": new state is " + getState());
			}

			@Override
			protected void waitForRenderResources(MemoryStack stack) {
				frameIndex = (frameIndex + 1) % NUM_FRAMES_IN_FLIGHT;
				fences[frameIndex].waitIfSubmitted();
				fences[frameIndex].reset();
				assertVkSuccess(vkResetCommandPool(
						xr.boilerInstance.vkDevice(), commandPools[frameIndex], 0
				), "ResetCommandPool", "Drawing");
			}

			@Override
			protected void recordRenderCommands(
					MemoryStack stack, XrFrameState frameState, int swapchainImageIndex, Matrix4f[] cameraMatrices
			) {
				Matrix4f leftHandMatrix = boiler.xr().locateSpace(
						stack, leftHandSpace, renderSpace, frameState.predictedDisplayTime(), "left hand"
				).createMatrix();
				if (leftHandMatrix != null) leftHandMatrix.scale(0.1f);
				Matrix4f rightHandMatrix = boiler.xr().locateSpace(
						stack, rightHandSpace, renderSpace, frameState.predictedDisplayTime(), "right hand"
				).createMatrix();
				if (rightHandMatrix != null) rightHandMatrix.scale(0.1f);

				var commands = CommandRecorder.begin(commandBuffers[frameIndex], xr.boilerInstance, stack, "Drawing");

				var colorAttachments = VkRenderingAttachmentInfoKHR.calloc(1, stack);
				commands.simpleColorRenderingAttachment(
						colorAttachments.get(0), swapchainImageViews[swapchainImageIndex],
						VK_ATTACHMENT_LOAD_OP_CLEAR, VK_ATTACHMENT_STORE_OP_STORE,
						1f, 0f, 0f, 1f
				);

				var depthAttachment = commands.simpleDepthRenderingAttachment(
						depthImage.vkImageView(), VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
						VK_ATTACHMENT_STORE_OP_DONT_CARE, 1f, 0
				);

				var dynamicRenderingInfo = VkRenderingInfoKHR.calloc(stack);
				dynamicRenderingInfo.sType$Default();
				dynamicRenderingInfo.renderArea().offset().set(0, 0);
				dynamicRenderingInfo.renderArea().extent().set(width, height);
				dynamicRenderingInfo.layerCount(2);
				dynamicRenderingInfo.viewMask(3);
				dynamicRenderingInfo.pColorAttachments(colorAttachments);
				dynamicRenderingInfo.pDepthAttachment(depthAttachment);

				vkCmdBeginRenderingKHR(commandBuffers[frameIndex], dynamicRenderingInfo);
				vkCmdBindPipeline(commandBuffers[frameIndex], VK_PIPELINE_BIND_POINT_GRAPHICS, graphicsPipeline);
				vkCmdBindDescriptorSets(
						commandBuffers[frameIndex], VK_PIPELINE_BIND_POINT_GRAPHICS,
						pipelineLayout, 0, stack.longs(descriptorSets[frameIndex]), null
				);
				vkCmdBindVertexBuffers(
						commandBuffers[frameIndex], 0,
						stack.longs(vertexBuffer.vkBuffer()), stack.longs(0)
				);
				vkCmdBindIndexBuffer(commandBuffers[frameIndex], indexBuffer.vkBuffer(), 0, VK_INDEX_TYPE_UINT32);

				var hostMatrixBuffer = memFloatBuffer(matrixBuffers[frameIndex].hostAddress(), 5 * 16);
				cameraMatrices[0].get(0, hostMatrixBuffer);
				cameraMatrices[1].get(16, hostMatrixBuffer);

				var floorMatrix = new Matrix4f();
				{
					var giFloor = session.prepareActionState(stack, scaleAction);
					var floorScale = session.getFloatAction(stack, giFloor, "floor scale");
					if (floorScale.currentState() > 0f) floorMatrix.scale(floorScale.currentState() * 3f);
				}
				floorMatrix.get(32, hostMatrixBuffer);
				if (leftHandMatrix != null) leftHandMatrix.get(48, hostMatrixBuffer);
				if (rightHandMatrix != null) rightHandMatrix.get(64, hostMatrixBuffer);

				var pushConstants = stack.callocInt(2);

				pushConstants.put(0, 0);
				pushConstants.put(1, 0);
				vkCmdPushConstants(
						commandBuffers[frameIndex], pipelineLayout,
						VK_SHADER_STAGE_FRAGMENT_BIT | VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstants
				);
				vkCmdDrawIndexed(commandBuffers[frameIndex], 3, 1, 0, 0, 0);

				if (leftHandMatrix != null) {
					var giLeftClick = session.prepareSubactionState(stack, handClickAction, pathLeftHand);
					var holdsLeft = session.getBooleanAction(stack, giLeftClick, "left click").currentState();
					pushConstants.put(0, holdsLeft ? 0 : 1);
					pushConstants.put(1, 1);
					vkCmdPushConstants(
							commandBuffers[frameIndex], pipelineLayout,
							VK_SHADER_STAGE_FRAGMENT_BIT | VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstants
					);
					vkCmdDrawIndexed(commandBuffers[frameIndex], 12, 1, 3, 0, 0);
				}
				if (rightHandMatrix != null) {
					var giRightClick = session.prepareSubactionState(stack, handClickAction, pathRightHand);
					var holdsRight = session.getBooleanAction(stack, giRightClick, "right click").currentState();
					pushConstants.put(0, holdsRight ? 0 : 1);
					pushConstants.put(1, 2);
					vkCmdPushConstants(
							commandBuffers[frameIndex], pipelineLayout,
							VK_SHADER_STAGE_FRAGMENT_BIT | VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstants
					);
					vkCmdDrawIndexed(commandBuffers[frameIndex], 12, 1, 3, 0, 0);
				}

				vkCmdEndRenderingKHR(commandBuffers[frameIndex]);
				commands.end();
			}

			@Override
			protected void submitRenderCommands() {
				xr.boilerInstance.queueFamilies().graphics().first().submit(
						commandBuffers[frameIndex], "Drawing", null, fences[frameIndex]
				);
			}
		}

		new HelloSessionLoop(session, renderSpace, swapchain, width, height).run();

		boiler.sync.fenceBank.returnFences(fences);
		for (var commandPool : commandPools) vkDestroyCommandPool(boiler.vkDevice(), commandPool, null);
		vkDestroyPipeline(boiler.vkDevice(), graphicsPipeline, null);
		vkDestroyPipelineLayout(boiler.vkDevice(), pipelineLayout, null);
		descriptorPool.destroy();
		descriptorSetLayout.destroy();
		for (long imageView : swapchainImageViews) {
			vkDestroyImageView(boiler.vkDevice(), imageView, null);
		}

		vertexBuffer.destroy(boiler);
		indexBuffer.destroy(boiler);
		for (var matrixBuffer : matrixBuffers) matrixBuffer.destroy(boiler);
		vkDestroyImageView(boiler.vkDevice(), depthImage.vkImageView(), null);
		vmaDestroyImage(boiler.vmaAllocator(), depthImage.vkImage(), depthImage.vmaAllocation());

		assertXrSuccess(xrDestroySpace(leftHandSpace), "DestroySpace", "left hand");
		assertXrSuccess(xrDestroySpace(rightHandSpace), "DestroySpace", "right hand");
		assertXrSuccess(xrDestroyAction(handPoseAction), "DestroyAction", "hand pose");
		assertXrSuccess(xrDestroyAction(handClickAction), "DestroyAction", "hand click");
		assertXrSuccess(xrDestroyActionSet(actionSet), "DestroyActionSet", null);

		assertXrSuccess(xrDestroySpace(renderSpace), "DestroySpace", "renderSpace");
		assertXrSuccess(xrDestroySwapchain(swapchain), "DestroySwapchain", null);

		assertXrSuccess(xrDestroySession(session.xrSession), "DestroySession", null);

		boiler.destroyInitialObjects();
	}
}
