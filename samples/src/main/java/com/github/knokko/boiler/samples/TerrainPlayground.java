package com.github.knokko.boiler.samples;

import com.github.knokko.boiler.buffers.MappedVkbBuffer;
import com.github.knokko.boiler.buffers.VkbBuffer;
import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.builders.WindowBuilder;
import com.github.knokko.boiler.builders.instance.ValidationFeatures;
import com.github.knokko.boiler.commands.CommandRecorder;
import com.github.knokko.boiler.commands.SingleTimeCommands;
import com.github.knokko.boiler.culling.FrustumCuller;
import com.github.knokko.boiler.descriptors.*;
import com.github.knokko.boiler.images.ImageBuilder;
import com.github.knokko.boiler.images.VkbImage;
import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.memory.MemoryBlock;
import com.github.knokko.boiler.memory.MemoryCombiner;
import com.github.knokko.boiler.memory.callbacks.CallbackUserData;
import com.github.knokko.boiler.memory.callbacks.SumAllocationCallbacks;
import com.github.knokko.boiler.pipelines.GraphicsPipelineBuilder;
import com.github.knokko.boiler.window.AcquiredImage;
import com.github.knokko.boiler.window.SwapchainResourceManager;
import com.github.knokko.boiler.synchronization.ResourceUsage;
import com.github.knokko.boiler.synchronization.TimelineInstant;
import com.github.knokko.boiler.synchronization.WaitSemaphore;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static java.lang.Math.*;
import static java.lang.Thread.sleep;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_FIFO_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.VK_API_VERSION_1_2;

public class TerrainPlayground {

	/**
	 * The width and height of the height image, in pixels
	 */
	private static final int HEIGHT_IMAGE_NUM_PIXELS = 3601;

	/**
	 * The real-world size of 1 pixel on the height image, in meters
	 */
	private static final float HEIGHT_IMAGE_PIXEL_SIZE = 30f;

	/**
	 * The real-world size of the height image, in meters
	 */
	private static final float HEIGHT_IMAGE_SIZE = HEIGHT_IMAGE_NUM_PIXELS * HEIGHT_IMAGE_PIXEL_SIZE;

	private static int minHeight = Integer.MAX_VALUE;
	private static int maxHeight = Integer.MIN_VALUE;
	private static HeightLookup coarseHeightLookup;
	private static HeightLookup fineHeightLookup;
	private static HeightLookup coarseDeltaHeightLookup;

	private static long createRenderPass(MemoryStack stack, BoilerInstance boiler, int depthFormat) {
		var attachments = VkAttachmentDescription.calloc(2, stack);
		var colorAttachment = attachments.get(0);
		colorAttachment.flags(0);
		colorAttachment.format(boiler.window().surfaceFormat);
		colorAttachment.samples(VK_SAMPLE_COUNT_1_BIT);
		colorAttachment.loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR);
		colorAttachment.storeOp(VK_ATTACHMENT_STORE_OP_STORE);
		colorAttachment.stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE);
		colorAttachment.stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE);
		colorAttachment.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
		colorAttachment.finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
		var depthAttachment = attachments.get(1);
		depthAttachment.flags(0);
		depthAttachment.format(depthFormat);
		depthAttachment.samples(VK_SAMPLE_COUNT_1_BIT);
		depthAttachment.loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR);
		depthAttachment.storeOp(VK_ATTACHMENT_STORE_OP_DONT_CARE);
		depthAttachment.stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE);
		depthAttachment.stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE);
		depthAttachment.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
		depthAttachment.finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

		var colorReference = VkAttachmentReference.calloc(1, stack);
		colorReference.attachment(0);
		colorReference.layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

		var depthReference = VkAttachmentReference.calloc();
		depthReference.attachment(1);
		depthReference.layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

		var subpass = VkSubpassDescription.calloc(1, stack);
		subpass.flags(0);
		subpass.pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS);
		subpass.pInputAttachments(null);
		subpass.colorAttachmentCount(1);
		subpass.pColorAttachments(colorReference);
		subpass.pResolveAttachments(null);
		subpass.pDepthStencilAttachment(depthReference);
		subpass.pPreserveAttachments(null);

		var dependencies = VkSubpassDependency.calloc(2, stack);
		var colorDependency = dependencies.get(0);
		colorDependency.srcSubpass(VK_SUBPASS_EXTERNAL);
		colorDependency.dstSubpass(0);
		colorDependency.srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
		colorDependency.srcAccessMask(0);
		colorDependency.dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
		colorDependency.dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);
		var depthDependency = dependencies.get(1);
		depthDependency.srcSubpass(VK_SUBPASS_EXTERNAL);
		depthDependency.dstSubpass(0);
		depthDependency.srcStageMask(VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT);
		depthDependency.srcAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);
		depthDependency.dstStageMask(VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT);
		depthDependency.dstAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);

		var ciRenderPass = VkRenderPassCreateInfo.calloc(stack);
		ciRenderPass.sType$Default();
		ciRenderPass.pAttachments(attachments);
		ciRenderPass.pSubpasses(subpass);
		ciRenderPass.pDependencies(dependencies);

		var pRenderPass = stack.callocLong(1);
		assertVkSuccess(vkCreateRenderPass(
				boiler.vkDevice(), ciRenderPass, CallbackUserData.RENDER_PASS.put(stack, boiler), pRenderPass
		), "CreateRenderPass", "TerrainPlayground");
		long renderPass = pRenderPass.get(0);
		boiler.debug.name(stack, renderPass, VK_OBJECT_TYPE_RENDER_PASS, "TerrainRendering");
		return renderPass;
	}

	private static VkbDescriptorSetLayout createDescriptorSetLayout(MemoryStack stack, BoilerInstance boiler) {
		var builder = new DescriptorSetLayoutBuilder(stack, 3);
		builder.set(0, 0, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_SHADER_STAGE_VERTEX_BIT);
		builder.set(1, 1, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, VK_SHADER_STAGE_VERTEX_BIT);
		builder.set(2, 2, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, VK_SHADER_STAGE_FRAGMENT_BIT);
		return builder.build(boiler, "DSLayout");
	}

	private static long createGroundPipelineLayout(MemoryStack stack, BoilerInstance boiler, long descriptorSetLayout) {
		var pushConstants = VkPushConstantRange.calloc(1, stack);
		pushConstants.offset(0);
		pushConstants.size(28);
		pushConstants.stageFlags(VK_SHADER_STAGE_VERTEX_BIT);

		return boiler.pipelines.createLayout(pushConstants, "GroundPipelineLayout", descriptorSetLayout);
	}

	private static long createGroundPipeline(MemoryStack stack, BoilerInstance boiler, long pipelineLayout, long renderPass) {
		var vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack);
		vertexInput.sType$Default();
		vertexInput.pVertexBindingDescriptions(null);
		vertexInput.pVertexAttributeDescriptions(null);

		var pipelineBuilder = new GraphicsPipelineBuilder(boiler, stack);
		pipelineBuilder.simpleShaderStages(
				"Ground", "com/github/knokko/boiler/samples/graphics/",
				"ground.vert.spv", "ground.frag.spv"
		);
		pipelineBuilder.ciPipeline.pVertexInputState(vertexInput);
		pipelineBuilder.simpleInputAssembly();
		pipelineBuilder.dynamicViewports(1);
		pipelineBuilder.simpleRasterization(VK_CULL_MODE_BACK_BIT);
		pipelineBuilder.noMultisampling();
		pipelineBuilder.simpleDepth(VK_COMPARE_OP_LESS_OR_EQUAL);
		pipelineBuilder.noColorBlending(1);
		pipelineBuilder.dynamicStates(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR);
		pipelineBuilder.ciPipeline.layout(pipelineLayout);
		pipelineBuilder.ciPipeline.renderPass(renderPass);
		pipelineBuilder.ciPipeline.subpass(0);
		return pipelineBuilder.build("GroundPipeline");
	}

	private static PersistentResources createHeightImages(BoilerInstance boiler, MemoryCombiner persistentCombiner) {
		try {
			var input = TerrainPlayground.class.getClassLoader().getResourceAsStream("com/github/knokko/boiler/samples/height/N44E006.hgt");
			assert input != null;
			var content = input.readAllBytes();
			input.close();

			int numValues = content.length / 2;
			if (content.length % 2 != 0) throw new RuntimeException("Size is odd");
			int gridSize = (int) sqrt(numValues);
			if (gridSize * gridSize != numValues) throw new RuntimeException(numValues + " is not a square");

			ShortBuffer hostHeightBuffer = ByteBuffer.wrap(content).order(ByteOrder.BIG_ENDIAN).asShortBuffer();
			ShortBuffer deltaHeightBuffer = ShortBuffer.allocate(hostHeightBuffer.capacity());

			var heightImage = persistentCombiner.addImage(new ImageBuilder(
					"HeightImage", gridSize, gridSize
			).texture().format(VK_FORMAT_R16_SINT), 0.75f);
			var normalImage = persistentCombiner.addImage(new ImageBuilder(
					"NormalImage", gridSize, gridSize
			).texture().format(VK_FORMAT_R8G8B8A8_SNORM), 0.75f);
			var persistentMemory = persistentCombiner.build(false);

			var stagingCombiner = new MemoryCombiner(boiler, "StagingMemory");
			var heightBuffer = stagingCombiner.addMappedBuffer(content.length, 2, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
			var normalBuffer = stagingCombiner.addMappedBuffer(
					4L * normalImage.width * normalImage.height, 4, VK_BUFFER_USAGE_TRANSFER_SRC_BIT
			);
			var stagingMemory = stagingCombiner.build(false);

			var heightHostBuffer = heightBuffer.shortBuffer();
			var normalHostBuffer = normalBuffer.byteBuffer();
			short previousValue = 0;
			for (int counter = 0; counter < numValues; counter++) {
				short value = hostHeightBuffer.get(counter);
				if (value != Short.MIN_VALUE) {
					heightHostBuffer.put(value);
					previousValue = value;
					if (value < minHeight) minHeight = value;
					if (value > maxHeight) maxHeight = value;
					if (counter == numValues / 2) System.out.println("middle value is " + value);
				} else heightHostBuffer.put(previousValue);
			}
			System.out.println("lowest is " + minHeight + " and highest is " + maxHeight);
			coarseHeightLookup = new HeightLookup(80, HEIGHT_IMAGE_NUM_PIXELS, hostHeightBuffer);
			fineHeightLookup = new HeightLookup(400, HEIGHT_IMAGE_NUM_PIXELS, hostHeightBuffer);

			for (int v = 0; v < gridSize; v++) {
				for (int u = 0; u < gridSize; u++) {
					int index = u + v * gridSize;
					if (u == gridSize - 1 || v == gridSize - 1) {
						normalHostBuffer.put(4 * index, (byte) 0);
						normalHostBuffer.put(4 * index + 1, (byte) 127);
						normalHostBuffer.put(4 * index + 2, (byte) 0);
						deltaHeightBuffer.put(index, (short) 0);
					} else {
						int heightIndex = u + v * gridSize;
						int currentHeight = heightHostBuffer.get(heightIndex);
						int du = heightHostBuffer.get(heightIndex + 1) - currentHeight;
						int dv = heightHostBuffer.get(heightIndex + gridSize) - currentHeight;

						var vectorX = new Vector3f(30f, du, 0f);
						var vectorZ = new Vector3f(0f, dv, 30f);
						var normal = vectorZ.cross(vectorX).normalize();
						normalHostBuffer.put(4 * index, (byte) (127 * normal.x));
						normalHostBuffer.put(4 * index + 1, (byte) (127 * normal.y));
						normalHostBuffer.put(4 * index + 2, (byte) (127 * normal.z));
						deltaHeightBuffer.put(index, (short) max(abs(du), abs(dv)));
					}
				}
			}

			coarseDeltaHeightLookup = new HeightLookup(600, HEIGHT_IMAGE_NUM_PIXELS, deltaHeightBuffer);

			SingleTimeCommands.submit(boiler, "StagingCopies", recorder -> {
				recorder.bulkTransitionLayout(null, ResourceUsage.TRANSFER_DEST, heightImage, normalImage);
				recorder.bulkCopyBufferToImage(new VkbImage[] { heightImage, normalImage }, new VkbBuffer[]{ heightBuffer, normalBuffer });
				recorder.transitionLayout(
						heightImage, ResourceUsage.TRANSFER_DEST,
						ResourceUsage.shaderRead(VK_PIPELINE_STAGE_VERTEX_SHADER_BIT)
				);
				recorder.transitionLayout(
						normalImage, ResourceUsage.TRANSFER_DEST,
						ResourceUsage.shaderRead(VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT)
				);
			}).destroy();

			stagingMemory.destroy(boiler);
			return new PersistentResources(persistentMemory, heightImage, normalImage);
		} catch (IOException shouldNotHappen) {
			throw new RuntimeException(shouldNotHappen);
		}
	}

	public static void main(String[] args) throws InterruptedException {
		var boiler = new BoilerBuilder(
				VK_API_VERSION_1_2, "TerrainPlayground", VK_MAKE_VERSION(0, 1, 0)
		)
				.validation(new ValidationFeatures(true, false, true, true))
				.forbidValidationErrors()
				.allocationCallbacks(new SumAllocationCallbacks())
				.addWindow(new WindowBuilder(1000, 800, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT).hideUntilFirstFrame())
				.requiredFeatures12("timeline semaphore", VkPhysicalDeviceVulkan12Features::timelineSemaphore)
				.featurePicker12((stack, supported, toEnable) -> toEnable.timelineSemaphore(true))
				.build();

		long debugMessenger;
		try (var stack = stackPush()) {
			debugMessenger = boiler.debug.createMessenger(
					stack, VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT,
					VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT, (data, severity, type) -> {
						System.out.println("test (" + data.messageIdNumber() + ") " + data.pMessageString());
					}, "Spectator"
			);
		}

		int numFramesInFlight = 3;

		var persistentCombiner = new MemoryCombiner(boiler, "PersistentMemory");
		var uniformBuffers = new MappedVkbBuffer[numFramesInFlight];
		for (int index = 0; index < numFramesInFlight; index++) {
			uniformBuffers[index] = persistentCombiner.addMappedDeviceLocalBuffer(
					64, boiler.deviceProperties.limits().minUniformBufferOffsetAlignment(),
					VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, 0.5f
			);
		}

		var persistent = createHeightImages(boiler, persistentCombiner);

		long renderPass;
		VkbDescriptorSetLayout descriptorSetLayout;
		long pipelineLayout;
		long groundPipeline;
		long vkDescriptorPool;
		long[] descriptorSets;
		int depthFormat = boiler.images.chooseDepthStencilFormat(
				VK_FORMAT_X8_D24_UNORM_PACK32, VK_FORMAT_D24_UNORM_S8_UINT, VK_FORMAT_D32_SFLOAT
		);
		long heightSampler;
		long normalSampler;
		try (var stack = stackPush()) {

			renderPass = createRenderPass(stack, boiler, depthFormat);

			descriptorSetLayout = createDescriptorSetLayout(stack, boiler);
			pipelineLayout = createGroundPipelineLayout(stack, boiler, descriptorSetLayout.vkDescriptorSetLayout);
			groundPipeline = createGroundPipeline(stack, boiler, pipelineLayout, renderPass);

			var descriptors = new DescriptorCombiner(boiler);
			descriptorSets = descriptors.addMultiple(descriptorSetLayout, numFramesInFlight);
			vkDescriptorPool = descriptors.build("DescriptorPool");

			heightSampler = boiler.images.createSampler(
					VK_FILTER_NEAREST, VK_SAMPLER_MIPMAP_MODE_NEAREST,
					VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE, 0f, 0f, false, "HeightSampler"
			);
			normalSampler = boiler.images.createSimpleSampler(
					VK_FILTER_LINEAR, VK_SAMPLER_MIPMAP_MODE_NEAREST, VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE, "NormalSampler"
			);

			var updater = new BulkDescriptorUpdater(
					boiler, null, 3 * numFramesInFlight,
					numFramesInFlight, 2 * numFramesInFlight
			);
			for (int frame = 0; frame < numFramesInFlight; frame++) {
				updater.writeUniformBuffer(descriptorSets[frame], 0, uniformBuffers[frame]);
				updater.writeImage(descriptorSets[frame], 1, persistent.heightImage.vkImageView, heightSampler);
				updater.writeImage(descriptorSets[frame], 2, persistent.normalImage.vkImageView, normalSampler);
			}
			updater.finish();
		}

		var commandPool = boiler.commands.createPool(
				VK_COMMAND_POOL_CREATE_TRANSIENT_BIT | VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT,
				boiler.queueFamilies().graphics().index(), "TerrainPool"
		);
		var commandBuffers = boiler.commands.createPrimaryBuffers(
				commandPool, numFramesInFlight, "TerrainCommands"
		);
		var timeline = boiler.sync.createTimelineSemaphore(numFramesInFlight - 1, "TerrainTimeline");

		long frameCounter = 0;
		var swapchainResources = new SwapchainResourceManager<SwapchainResources, ImageResources>() {

			@Override
			protected SwapchainResources createSwapchain(int width, int height, int numImages) {
				var depthImages = new VkbImage[numImages];
				var combiner = depthCombiner(depthImages, width, height);
				var memory = combiner.build(false);
				return new SwapchainResources(memory, depthImages);
			}

			@Override
			protected ImageResources createImage(SwapchainResources swapchain, AcquiredImage swapchainImage) {
				var depthImage = swapchain.depthImages[swapchainImage.index()];
				long framebuffer = boiler.images.createFramebuffer(
						renderPass, swapchainImage.width(), swapchainImage.height(),
						"TerrainFramebuffer", swapchainImage.image().vkImageView, depthImage.vkImageView
				);
				return new ImageResources(framebuffer, depthImage);
			}

			@Override
			protected void destroySwapchain(SwapchainResources resources){
				resources.depthMemory().destroy(boiler);
			}

			@Override
			protected void destroyImage(ImageResources resources){
				try (var stack = stackPush()) {
					vkDestroyFramebuffer(
							boiler.vkDevice(), resources.framebuffer,
							CallbackUserData.FRAME_BUFFER.put(stack, boiler)
					);
				}
			}

			@Override
			protected SwapchainResources recreateSwapchain(SwapchainResources old, int width, int height, int numImages) {
				var depthImages = new VkbImage[numImages];
				var combiner = depthCombiner(depthImages, width, height);
				var memory = combiner.buildAndRecycle(old.depthMemory);
				return new SwapchainResources(memory, depthImages);
			}

			private MemoryCombiner depthCombiner(VkbImage[] depthImages, int width, int height) {
				var combiner = new MemoryCombiner(boiler, "DepthMemory");
				for (int index = 0; index < depthImages.length; index++) {
					depthImages[index] = combiner.addImage(new ImageBuilder(
							"DepthImage" + index, width, height
					).depthAttachment(depthFormat), 1f);
				}
				return combiner;
			}
		};

		long referenceTime = System.currentTimeMillis();
		long referenceFrames = 0;

		class Camera {
			float yaw = 0f;
			float pitch = 0f;

			float x = 0f;
			float y = 2055.3f + 1.7f;
			float z = 0f;
		}

		class CameraController {
			double oldX = Double.NaN;
			double oldY = Double.NaN;
		}

		var camera = new Camera();
		var cameraController = new CameraController();

		glfwSetKeyCallback(boiler.window().handle, ((window, key, scancode, action, mods) -> {
			float dx = 0f, dy = 0f, dz = 0f;
			if (key == GLFW_KEY_A) dx = -1f;
			if (key == GLFW_KEY_D) dx = 1f;
			if (key == GLFW_KEY_S) dz = 1f;
			if (key == GLFW_KEY_W) dz = -1f;
			if (key == GLFW_KEY_Q) dy = -1f;
			if (key == GLFW_KEY_E) dy = 1f;

			float scale = 1f;
			if ((mods & GLFW_MOD_SHIFT) != 0) scale *= 0.1f;
			if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS) scale *= 100f;

			camera.x += dx * scale;
			camera.y += dy * scale;
			camera.z += dz * scale;
		}));

		//noinspection resource
		glfwSetCursorPosCallback(boiler.window().handle, (window, x, y) -> {
			if (!Double.isNaN(cameraController.oldX) && glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS) {
				double dx = x - cameraController.oldX;
				double dy = y - cameraController.oldY;
				camera.yaw += (float) (0.5 * dx);
				camera.pitch -= (float) (0.2 * dy);
				if (camera.pitch < -90) camera.pitch = -90;
				if (camera.pitch > 90) camera.pitch = 90;
				if (camera.yaw < 0) camera.yaw += 360;
				if (camera.yaw > 360) camera.yaw -= 360;
			}

			cameraController.oldX = x;
			cameraController.oldY = y;
		});

		while (!glfwWindowShouldClose(boiler.window().handle)) {
			glfwPollEvents();

			long currentTime = System.currentTimeMillis();
			if (currentTime > 1000 + referenceTime) {
				System.out.println("FPS is " + (frameCounter - referenceFrames));
				var allocationCallbacks = (SumAllocationCallbacks) boiler.allocationCallbacks;
				System.out.println("Simple allocation callbacks: " + allocationCallbacks.copySimpleSizes());
				System.out.println("INTERNAL allocation callbacks: " + allocationCallbacks.copyInternalSizes());
				referenceTime = currentTime;
				referenceFrames = frameCounter;
			}

			int presentMode = boiler.window().supportedPresentModes.contains(VK_PRESENT_MODE_MAILBOX_KHR) ?
					VK_PRESENT_MODE_MAILBOX_KHR : VK_PRESENT_MODE_FIFO_KHR;
			try (var stack = stackPush()) {
				var swapchainImage = boiler.window().acquireSwapchainImageWithSemaphore(presentMode);
				if (swapchainImage == null) {
					//noinspection BusyWait
					sleep(100);
					continue;
				}

				var imageResources = swapchainResources.get(swapchainImage);
				WaitSemaphore[] waitSemaphores = {new WaitSemaphore(
						swapchainImage.acquireSemaphore(), VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
				)};

				int frameIndex = (int) (frameCounter % numFramesInFlight);
				var commandBuffer = commandBuffers[frameIndex];
				timeline.waitUntil(frameCounter);

				var recorder = CommandRecorder.begin(commandBuffer, boiler, stack, "TerrainDraw");

				var clearValues = VkClearValue.calloc(2, stack);
				var clearColor = clearValues.get(0);
				clearColor.color().float32(stack.floats(0.2f, 0.8f, 0.6f, 1f));
				var clearDepth = clearValues.get(1);
				clearDepth.depthStencil().set(1f, 0);

				var biRenderPass = VkRenderPassBeginInfo.calloc(stack);
				biRenderPass.sType$Default();
				biRenderPass.renderPass(renderPass);
				biRenderPass.framebuffer(imageResources.framebuffer);
				biRenderPass.renderArea().offset().set(0, 0);
				biRenderPass.renderArea().extent().set(swapchainImage.width(), swapchainImage.height());
				biRenderPass.clearValueCount(2);
				biRenderPass.pClearValues(clearValues);

				vkCmdBeginRenderPass(commandBuffer, biRenderPass, VK_SUBPASS_CONTENTS_INLINE);
				vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, groundPipeline);
				recorder.dynamicViewportAndScissor(swapchainImage.width(), swapchainImage.height());
				recorder.bindGraphicsDescriptors(pipelineLayout, descriptorSets[frameIndex]);

				float fieldOfView = 45f;
				float aspectRatio = (float) swapchainImage.width() / (float) swapchainImage.height();

				float nearPlane = 0.1f;
				float farPlane = 50_000f;
				var cameraMatrix = new Matrix4f()
						.scale(1f, -1f, 1f)
						.perspective(
								(float) toRadians(fieldOfView), aspectRatio,
								nearPlane, farPlane, true
						)
						.rotateX((float) toRadians(-camera.pitch))
						.rotateY((float) toRadians(camera.yaw));
				cameraMatrix.get(uniformBuffers[frameIndex].floatBuffer());

				var frustumCuller = new FrustumCuller(
						new Vector3f(), camera.yaw, camera.pitch, aspectRatio, fieldOfView, nearPlane, farPlane
				);
				var fragmentsToRender = new ArrayList<TerrainFragment>();
				float cameraU = 2f * camera.x / HEIGHT_IMAGE_SIZE + 0.5f;
				float cameraV = 2f * camera.z / HEIGHT_IMAGE_SIZE + 0.5f;
				partitionTerrainSpace(cameraU, cameraV, 0.0002f, 1.5f, 13, fragmentsToRender);

				int quadCount = 0;
				int fragmentCount = 0;
				fragmentsToRender.removeIf(fragment -> {
					float minX = (fragment.minU - cameraU) * HEIGHT_IMAGE_SIZE;
					float minZ = (fragment.minV - cameraV) * HEIGHT_IMAGE_SIZE;
					float maxX = (fragment.maxU - cameraU) * HEIGHT_IMAGE_SIZE;
					float maxZ = (fragment.maxV - cameraV) * HEIGHT_IMAGE_SIZE;
					float threshold = 0.05f;

					float fragmentSize = max(fragment.maxU - fragment.minU, fragment.maxV - fragment.minV);
					var heightLookup = fragmentSize > threshold ? coarseHeightLookup : fineHeightLookup;
					short[] heightBounds = heightLookup.getHeights(fragment.minU, fragment.minV, fragment.maxU, fragment.maxV);

					var aabb = new FrustumCuller.AABB(minX, heightBounds[0] - camera.y, minZ, maxX, heightBounds[1] - camera.y, maxZ);
					return frustumCuller.shouldCullAABB(aabb);
				});

				var pushConstants = stack.calloc(28);

				var divisorMap = new HashMap<Integer, Integer>();
				for (var fragment : fragmentsToRender) {
					short maxDelta = coarseDeltaHeightLookup.getHeights(fragment.minU, fragment.minV, fragment.maxU, fragment.maxV)[1];
					int divisor = 1;
					if (maxDelta > 80) divisor = 2;
					if (maxDelta > 110) divisor = 3;
					if (maxDelta > 150) divisor = 4;
					divisorMap.put(divisor, divisorMap.getOrDefault(divisor, 0) + 1);

					pushConstants.putFloat(0, fragment.minX(cameraU));
					pushConstants.putFloat(4, 0f - camera.y);
					pushConstants.putFloat(8, fragment.minZ(cameraV));
					pushConstants.putFloat(12, fragment.quadSize / divisor);
					pushConstants.putFloat(16, fragment.minU);
					pushConstants.putFloat(20, fragment.minV);
					pushConstants.putInt(24, fragment.numColumns() * divisor);

					vkCmdPushConstants(commandBuffer, pipelineLayout, VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstants);
					int numQuads = fragment.numRows() * fragment.numColumns() * divisor * divisor;
					vkCmdDraw(commandBuffer, 6 * numQuads, 1, 0, 0);
					quadCount += numQuads;
					fragmentCount += 1;
				}
				vkCmdEndRenderPass(commandBuffer);
				if (Math.random() < 0.002) {
					System.out.println("Drew " + quadCount + " quads in " + fragmentCount + " fragment with camera yaw " + camera.yaw);
				}

				recorder.end();

				var timelineFinished = new TimelineInstant(timeline, frameCounter + numFramesInFlight);
				boiler.queueFamilies().graphics().first().submit(
						commandBuffer, "TerrainDraw", waitSemaphores, null,
						new long[]{ swapchainImage.presentSemaphore() }, null, timelineFinished
				);

				boiler.window().presentSwapchainImage(swapchainImage, timelineFinished);
				frameCounter += 1;
			}
		}

		assertVkSuccess(vkDeviceWaitIdle(boiler.vkDevice()), "DeviceWaitIdle", "FinishTerrainPlayground");
		timeline.destroy();

		try (var stack = stackPush()) {
			vkDestroyCommandPool(boiler.vkDevice(), commandPool, CallbackUserData.COMMAND_POOL.put(stack, boiler));

			vkDestroyDescriptorPool(boiler.vkDevice(), vkDescriptorPool, CallbackUserData.DESCRIPTOR_POOL.put(stack, boiler));
			vkDestroyPipeline(boiler.vkDevice(), groundPipeline, CallbackUserData.PIPELINE.put(stack, boiler));
			vkDestroyPipelineLayout(boiler.vkDevice(), pipelineLayout, CallbackUserData.PIPELINE_LAYOUT.put(stack, boiler));
			vkDestroyDescriptorSetLayout(
					boiler.vkDevice(), descriptorSetLayout.vkDescriptorSetLayout,
					CallbackUserData.DESCRIPTOR_SET_LAYOUT.put(stack, boiler)
			);
			vkDestroyRenderPass(boiler.vkDevice(), renderPass, CallbackUserData.RENDER_PASS.put(stack, boiler));
			persistent.memory().destroy(boiler);
			vkDestroySampler(boiler.vkDevice(), heightSampler, CallbackUserData.SAMPLER.put(stack, boiler));
			vkDestroySampler(boiler.vkDevice(), normalSampler, CallbackUserData.SAMPLER.put(stack, boiler));

			if (boiler.debug.hasDebug) vkDestroyDebugUtilsMessengerEXT(
					boiler.vkInstance(), debugMessenger, CallbackUserData.DEBUG_MESSENGER.put(stack, boiler)
			);
		}
		boiler.destroyInitialObjects();

		var allocationCallbacks = (SumAllocationCallbacks) boiler.allocationCallbacks;
		System.out.println("Final simple allocation callbacks: " + allocationCallbacks.copySimpleSizes());
		System.out.println("Final INTERNAL allocation callbacks: " + allocationCallbacks.copyInternalSizes());
	}

	private record SwapchainResources(
			MemoryBlock depthMemory,
			VkbImage[] depthImages
	) {}

	private record ImageResources(
			long framebuffer,
			VkbImage depthImage
	) { }

	private record TerrainFragment(
			float minU,
			float minV,
			float maxU,
			float maxV,
			float quadSize
	) {
		int numColumns() {
			return (int) ceil((maxU - minU) * HEIGHT_IMAGE_SIZE / quadSize);
		}

		int numRows() {
			return (int) ceil((maxV - minV) * HEIGHT_IMAGE_SIZE / quadSize);
		}

		float minX(float cameraU) {
			return (minU - cameraU) * HEIGHT_IMAGE_SIZE;
		}

		float minZ(float cameraV) {
			return (minV - cameraV) * HEIGHT_IMAGE_SIZE;
		}

		float maxX(float cameraU) {
			return (maxU - cameraU) * HEIGHT_IMAGE_SIZE;
		}

		float maxZ(float cameraV) {
			return (maxV - cameraV) * HEIGHT_IMAGE_SIZE;
		}
	}

	private static void addPartitionFragment(
			float cameraU, float cameraV, int dx, int dy,
			float fragmentSize, float quadSize, Collection<TerrainFragment> fragments, int exponent
	) {
		float offset = exponent == 0 ? -0.5f : 0f;
		float minU = cameraU + (dx + offset) * fragmentSize;
		float minV = cameraV + (dy + offset) * fragmentSize;
		float maxU = minU + fragmentSize;
		float maxV = minV + fragmentSize;

		// The margin prevents terrain holes that would spawn due to FP rounding errors
		float margin = 0.02f * fragmentSize;
		minU -= margin;
		minV -= margin;
		maxU += margin;
		maxV += margin;

		if (maxU <= 0f || maxV <= 0f || minU >= 1f || minV >= 1f) return;

		fragments.add(new TerrainFragment(max(minU, 0f), max(minV, 0f), min(maxU, 1f), min(maxV, 1f), quadSize));
	}

	private static float multipleOf(float value, float factor) {
		return factor * (int) (value / factor);
	}

	private static void partitionTerrainSpace(
			float cameraU, float cameraV, float initialFragmentSize, float initialQuadSize, int maxExponent,
			Collection<TerrainFragment> fragments
	) {
		float fragmentSize = initialFragmentSize;
		float quadSize = initialQuadSize;

		float cameraGranularity = 3f * fragmentSize;
		cameraU = multipleOf(cameraU, cameraGranularity);
		cameraV = multipleOf(cameraV, cameraGranularity);

		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				addPartitionFragment(cameraU, cameraV, dx, dy, fragmentSize, quadSize, fragments, 0);
			}
		}

		int exponent = 1;
		while (exponent <= maxExponent) {
			double oldMinU = fragments.stream().mapToDouble(fragment -> fragment.minU).min().getAsDouble();

			quadSize *= 1.45f;
			fragmentSize *= 1.5f;

			int[] rowSizes = {3, 4, 5, 6};
			if (exponent == 1) rowSizes = new int[]{2, 3};
			if (exponent >= 3) rowSizes = new int[]{5, 6};

			for (int rowSize : rowSizes) {
				int dx = -rowSize;
				int dy = -rowSize;

				for (; dx < rowSize - 1; dx++)
					addPartitionFragment(cameraU, cameraV, dx, dy, fragmentSize, quadSize, fragments, exponent);
				for (; dy < rowSize - 1; dy++)
					addPartitionFragment(cameraU, cameraV, dx, dy, fragmentSize, quadSize, fragments, exponent);
				for (; dx > -rowSize; dx--)
					addPartitionFragment(cameraU, cameraV, dx, dy, fragmentSize, quadSize, fragments, exponent);
				for (; dy > -rowSize; dy--)
					addPartitionFragment(cameraU, cameraV, dx, dy, fragmentSize, quadSize, fragments, exponent);
			}

			double minU = fragments.stream().mapToDouble(fragment -> fragment.minU).min().getAsDouble();
			double testU = minU + fragmentSize * rowSizes.length;
			double error = testU - oldMinU;

			if (abs(error) > 0.03f * fragmentSize && minU > 0) {
				System.out.printf("%d: fragmentSize = %.5f and minU = %.5f and testU = %.5f -> error = %.5f\n", exponent, fragmentSize, minU, testU, error);
				throw new Error("Too large! abort");
			}

			exponent += 1;
		}
	}

	private static class HeightLookup {

		private final int size;

		private final short[] minHeights, maxHeights;

		HeightLookup(int size, int fullSize, ShortBuffer heights) {
			this.size = size;
			this.minHeights = new short[size * size];
			this.maxHeights = new short[size * size];

			Arrays.fill(minHeights, Short.MAX_VALUE);
			Arrays.fill(maxHeights, Short.MIN_VALUE);

			for (int v = 0; v < fullSize; v++) {
				int ownV = size * v / fullSize;
				for (int u = 0; u < fullSize; u++) {
					int ownU = size * u / fullSize;
					int fullIndex = u + fullSize * v;
					int ownIndex = ownU + size * ownV;
					minHeights[ownIndex] = (short) min(minHeights[ownIndex], heights.get(fullIndex));
					maxHeights[ownIndex] = (short) max(maxHeights[ownIndex], heights.get(fullIndex));
				}
			}
		}

		short[] getHeights(float minU, float minV, float maxU, float maxV) {

			int minIntU = (int) (minU * size) - 1;
			int minIntV = (int) (minV * size) - 1;
			int maxIntU = (int) ceil(maxU * size) + 1;
			int maxIntV = (int) ceil(maxV * size) + 1;
			if (minIntU < 0) minIntU = 0;
			if (minIntV < 0) minIntV = 0;
			if (maxIntU >= size) maxIntU = size - 1;
			if (maxIntV >= size) maxIntV = size - 1;
			short minHeight = Short.MAX_VALUE;
			short maxHeight = Short.MIN_VALUE;

			for (int intU = minIntU; intU <= maxIntU; intU++) {
				for (int intV = minIntV; intV <= maxIntV; intV++) {
					minHeight = (short) min(minHeight, minHeights[intV * size + intU]);
					maxHeight = (short) max(maxHeight, maxHeights[intV * size + intU]);
				}
			}

			return new short[]{minHeight, maxHeight};
		}
	}

	private record PersistentResources(MemoryBlock memory, VkbImage heightImage, VkbImage normalImage) {}
}
