package com.github.knokko.boiler.samples;

import com.github.knokko.boiler.buffers.MappedVkbBufferRange;
import com.github.knokko.boiler.buffers.SharedMappedBufferBuilder;
import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.builders.xr.BoilerXrBuilder;
import com.github.knokko.boiler.commands.CommandRecorder;
import com.github.knokko.boiler.commands.SingleTimeCommands;
import com.github.knokko.boiler.descriptors.SharedDescriptorPool;
import com.github.knokko.boiler.descriptors.SharedDescriptorPoolBuilder;
import com.github.knokko.boiler.descriptors.VkbDescriptorSetLayout;
import com.github.knokko.boiler.images.ImageBuilder;
import com.github.knokko.boiler.images.VkbImage;
import com.github.knokko.boiler.memory.SharedMemoryBuilder;
import com.github.knokko.boiler.pipelines.GraphicsPipelineBuilder;
import com.github.knokko.boiler.synchronization.ResourceUsage;
import com.github.knokko.boiler.xr.SuggestedBindingsBuilder;
import com.github.knokko.boiler.xr.VkbSession;
import com.github.knokko.boiler.xr.SessionLoop;
import org.joml.Matrix4f;
import org.lwjgl.openxr.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.function.Supplier;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static com.github.knokko.boiler.exceptions.OpenXrFailureException.assertXrSuccess;
import static com.github.knokko.boiler.utilities.ColorPacker.rgb;
import static org.lwjgl.openxr.XR10.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK13.*;

public class HelloXR {

	private static final int NUM_FRAMES_IN_FLIGHT = 2;

	private static BufferedImage resourceImage(String name) {
		var input = HelloXR.class.getResourceAsStream("screenshots/" + name + ".png");
		try {
			assert input != null;
			var result = ImageIO.read(input);
			input.close();
			return result;
		} catch (IOException shouldNotHappen) {
			throw new Error(shouldNotHappen);
		}
	}

	public static void main(String[] args) throws InterruptedException {
		var boiler = new BoilerBuilder(
				VK_API_VERSION_1_3, "HelloXR", 1
		)
				.validation()
				.enableDynamicRendering()
				.printDeviceRejectionInfo()
				.requiredFeatures11(VkPhysicalDeviceVulkan11Features::multiview)
				.requiredFeatures12(VkPhysicalDeviceVulkan12Features::shaderSampledImageArrayNonUniformIndexing)
				.featurePicker11((stack, supported, enabled) -> enabled.multiview(true))
				.featurePicker12((stack, supported, enabled) -> enabled.shaderSampledImageArrayNonUniformIndexing(true))
				.xr(new BoilerXrBuilder())
				.build();

		var sharedMemoryBuilder = new SharedMemoryBuilder(boiler);
		var sharedBufferBuilder = new SharedMappedBufferBuilder(boiler);
		long sampler = boiler.images.createSimpleSampler(
				VK_FILTER_LINEAR, VK_SAMPLER_MIPMAP_MODE_NEAREST,
				VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER, "ImageSampler"
		);

		var bufferedGrass = resourceImage("grass");
		var bufferedMountains1 = resourceImage("chocoMountains");
		var bufferedMountains2 = resourceImage("rainbowMountains");
		Supplier<VkbImage> getGrassImage, getMountainImage1, getMountainImage2;
		Supplier<MappedVkbBufferRange> getGrassStagingBuffer, getMountainStagingBuffer1, getMountainStagingBuffer2;
		{
			getGrassImage = sharedMemoryBuilder.add(new ImageBuilder(
					"GrassImage", bufferedGrass.getWidth(), bufferedGrass.getHeight()
			).texture());
			getMountainImage1 = sharedMemoryBuilder.add(new ImageBuilder(
					"ChocoMountainsImage", bufferedMountains1.getWidth(), bufferedMountains1.getHeight()
			).texture());
			getMountainImage2 = sharedMemoryBuilder.add(new ImageBuilder(
					"RainbowMountainsImage", bufferedMountains2.getWidth(), bufferedMountains2.getHeight()
			).texture());

			getGrassStagingBuffer = sharedBufferBuilder.add(
					4L * bufferedGrass.getWidth() * bufferedGrass.getHeight(), 4L
			);
			getMountainStagingBuffer1 = sharedBufferBuilder.add(
					4L * bufferedMountains1.getWidth() * bufferedMountains1.getHeight(), 4L
			);
			getMountainStagingBuffer2 = sharedBufferBuilder.add(
					4L * bufferedMountains2.getWidth() * bufferedMountains2.getHeight(), 4L
			);
		}
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
		var getDepthImage = sharedMemoryBuilder.add(new ImageBuilder(
				"DepthImage", width, height
		).depthAttachment(depthFormat).arrayLayers(2));

		var commandPools = boiler.commands.createPools(
				VK_COMMAND_POOL_CREATE_TRANSIENT_BIT, boiler.queueFamilies().graphics().index(),
				NUM_FRAMES_IN_FLIGHT, "Drawing"
		);
		var commandBuffers = boiler.commands.createPrimaryBufferPerPool("Drawing", commandPools);
		var fences = boiler.sync.fenceBank.borrowFences(NUM_FRAMES_IN_FLIGHT, false, "Drawing");

		int colorVertexSize = (3 + 3) * 4;
		int imageVertexSize = (3 + 2 + 1) * 4;
		var getColorVertexBuffer = sharedBufferBuilder.add(4 * colorVertexSize, 12L);
		var getImageVertexBuffer = sharedBufferBuilder.add(12 * imageVertexSize, 24L);
		var getColorIndexBuffer = sharedBufferBuilder.add(5L * 3L * 4L, 4L);
		var getImageIndexBuffer = sharedBufferBuilder.add(4L * 6L * 4L, 4L);

		var matrixBuffers = new MappedVkbBufferRange[NUM_FRAMES_IN_FLIGHT];
		@SuppressWarnings("unchecked")
		Supplier<MappedVkbBufferRange>[] getMatrixBuffers = new Supplier[NUM_FRAMES_IN_FLIGHT];
		long minUniformBufferOffsetAlignment;
		try (var stack = stackPush()) {
			var properties = VkPhysicalDeviceProperties.calloc(stack);
			vkGetPhysicalDeviceProperties(boiler.vkPhysicalDevice(), properties);
			minUniformBufferOffsetAlignment = properties.limits().minUniformBufferOffsetAlignment();
		}
		for (int index = 0; index < NUM_FRAMES_IN_FLIGHT; index++) {
			getMatrixBuffers[index] = sharedBufferBuilder.add(5L * 64L, minUniformBufferOffsetAlignment);
		}

		sharedMemoryBuilder.add(
				sharedBufferBuilder, VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_VERTEX_BUFFER_BIT |
						VK_BUFFER_USAGE_INDEX_BUFFER_BIT | VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, "SharedBuffer"
		);
		var sharedMemoryAllocation = sharedMemoryBuilder.allocate("SharedMemory", false);
		for (int index = 0; index < NUM_FRAMES_IN_FLIGHT; index++) {
			matrixBuffers[index] = getMatrixBuffers[index].get();
		}

		var colorVertexBuffer = getColorVertexBuffer.get();
		var colorIndexBuffer = getColorIndexBuffer.get();
		var imageVertexBuffer = getImageVertexBuffer.get();
		var imageIndexBuffer = getImageIndexBuffer.get();
		var depthImage = getDepthImage.get();

		{
			var hostVertexBuffer = colorVertexBuffer.floatBuffer();
			hostVertexBuffer.put(-1f).put(0f).put(1f); // vertex1.position = (-1, 0, 1)
			hostVertexBuffer.put(1f).put(0f).put(0f); // vertex1.color = red
			hostVertexBuffer.put(1f).put(0f).put(1f); // vertex2.position = (1, 0, 1)
			hostVertexBuffer.put(0f).put(1f).put(0f); // vertex2.color = green
			hostVertexBuffer.put(0f).put(0f).put(-1f); // vertex3.position = (0, 0, -1)
			hostVertexBuffer.put(0f).put(0f).put(1f); // vertex3.color = blue
			hostVertexBuffer.put(0f).put(1f).put(0f); // vertex4.position = (0, 1, 0)
			hostVertexBuffer.put(0.5f).put(0.5f).put(0.5f); // vertex4.color = grey
		}
		{
			var hostVertexBuffer = imageVertexBuffer.byteBuffer();

			// First quad
			hostVertexBuffer.putFloat(-0.7f).putFloat(0.2f).putFloat(-1f); // bottomLeft = (-0.7, 0.2f, -1f)
			hostVertexBuffer.putInt(0).putFloat(0f).putFloat(0f); // bottomLeft has texture 0 with coordinates (0, 0)
			hostVertexBuffer.putFloat(0.7f).putFloat(0.2f).putFloat(-1f); // bottomRight = (0.7, 0.2f, -1f)
			hostVertexBuffer.putInt(0).putFloat(1f).putFloat(0f); // bottomRight has texture 0 with coordinates (1, 0)
			hostVertexBuffer.putFloat(0.7f).putFloat(0.8f).putFloat(-1f); // topRight = (0.7, 0.8f, -1f)
			hostVertexBuffer.putInt(0).putFloat(1f).putFloat(1f); // topRight has texture 0 with coordinates (1, 1)
			hostVertexBuffer.putFloat(-0.7f).putFloat(0.8f).putFloat(-1f); // topLeft = (-0.7, 0.8f, -1f)
			hostVertexBuffer.putInt(0).putFloat(0f).putFloat(1f); // topLeft has texture 0 with coordinates (0, 1)

			// Second quad
			hostVertexBuffer.putFloat(1f).putFloat(0.2f).putFloat(-1f); // bottomLeft = (1f, 0.2f, -1f)
			hostVertexBuffer.putInt(1).putFloat(0f).putFloat(0f); // bottomLeft has texture 1 with coordinates (0, 0)
			hostVertexBuffer.putFloat(0.3f).putFloat(0.2f).putFloat(0.5f); // bottomRight = (0.3, 0.2f, 0.5f)
			hostVertexBuffer.putInt(1).putFloat(1f).putFloat(0f); // bottomRight has texture 1 with coordinates (1, 0)
			hostVertexBuffer.putFloat(0.3f).putFloat(1.2f).putFloat(0.5f); // topRight = (0.3, 1.2f, 0.5f)
			hostVertexBuffer.putInt(1).putFloat(1f).putFloat(1f); // topRight has texture 1 with coordinates (1, 1)
			hostVertexBuffer.putFloat(1f).putFloat(1.2f).putFloat(-1f); // topLeft = (1f, 1.2f, -1f)
			hostVertexBuffer.putInt(1).putFloat(0f).putFloat(1f); // topLeft has texture 1 with coordinates (0, 1)

			// Third quad
			hostVertexBuffer.putFloat(-0.3f).putFloat(0.2f).putFloat(0.5f); // bottomLeft = (-0.3f, 0.2f, 0.5f)
			hostVertexBuffer.putInt(2).putFloat(0f).putFloat(0f); // bottomLeft has texture 2 with coordinates (0, 0)
			hostVertexBuffer.putFloat(-0.3f).putFloat(0.2f).putFloat(-1f); // bottomRight = (-0.3, 0.2f, -1f)
			hostVertexBuffer.putInt(2).putFloat(1f).putFloat(0f); // bottomRight has texture 2 with coordinates (1, 0)
			hostVertexBuffer.putFloat(-0.3f).putFloat(1.2f).putFloat(-1f); // topRight = (-0.3, 1.2f, -1f)
			hostVertexBuffer.putInt(2).putFloat(1f).putFloat(1f); // topRight has texture 2 with coordinates (1, 1)
			hostVertexBuffer.putFloat(-0.3f).putFloat(1.2f).putFloat(0.5f); // topLeft = (-0.3f, 1.2f, 0.5f)
			hostVertexBuffer.putInt(2).putFloat(0f).putFloat(1f); // topLeft has texture 2 with coordinates (0, 1)
		}
		{
			var hostIndexBuffer = colorIndexBuffer.intBuffer();
			hostIndexBuffer.put(0).put(1).put(2); // bottom triangle, pointing up
			hostIndexBuffer.put(2).put(1).put(0); // bottom triangle, pointing down
			hostIndexBuffer.put(0).put(1).put(3); // back of the hand triangle
			hostIndexBuffer.put(1).put(2).put(3); // right of the hand triangle
			hostIndexBuffer.put(2).put(0).put(3); // left of the hand triangle
		}
		{
			var hostIndexBuffer = imageIndexBuffer.intBuffer();
			for (int offset = 0; offset < 12; offset += 4) {
				hostIndexBuffer.put(offset).put(offset + 1).put(offset + 2);
				hostIndexBuffer.put(offset + 2).put(offset + 3).put(offset);
			}
		}

		{
			boiler.buffers.encodeBufferedImageIntoRangeRGBA(getGrassStagingBuffer.get(), bufferedGrass);
			boiler.buffers.encodeBufferedImageIntoRangeRGBA(getMountainStagingBuffer1.get(), bufferedMountains1);
			boiler.buffers.encodeBufferedImageIntoRangeRGBA(getMountainStagingBuffer2.get(), bufferedMountains2);

			var singleTime = new SingleTimeCommands(boiler);
			singleTime.submit("StagingCopy", recorder -> {
				VkbImage[] images = { getGrassImage.get(), getMountainImage1.get(), getMountainImage2.get() };
				for (VkbImage image : images) recorder.transitionLayout(image, null, ResourceUsage.TRANSFER_DEST);
				recorder.copyBufferToImage(getGrassImage.get(), getGrassStagingBuffer.get().range());
				recorder.copyBufferToImage(getMountainImage1.get(), getMountainStagingBuffer1.get().range());
				recorder.copyBufferToImage(getMountainImage2.get(), getMountainStagingBuffer2.get().range());
				for (VkbImage image : images) recorder.transitionLayout(
						image, ResourceUsage.TRANSFER_DEST, ResourceUsage.shaderRead(VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT)
				);
			});
			singleTime.destroy();
		}

		VkbDescriptorSetLayout colorDescriptorSetLayout, imageDescriptorSetLayout;
		SharedDescriptorPool sharedDescriptorPool;
		long[] colorDescriptorSets, imageDescriptorSets;
		long colorPipelineLayout, imagePipelineLayout;
		long colorPipeline, imagePipeline;
		try (var stack = stackPush()) {

			var colorLayoutBindings = VkDescriptorSetLayoutBinding.calloc(1, stack);
			boiler.descriptors.binding(colorLayoutBindings, 0, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_SHADER_STAGE_VERTEX_BIT);

			var imageLayoutBindings = VkDescriptorSetLayoutBinding.calloc(2, stack);
			boiler.descriptors.binding(imageLayoutBindings, 0, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_SHADER_STAGE_VERTEX_BIT);
			boiler.descriptors.binding(imageLayoutBindings, 1, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, VK_SHADER_STAGE_FRAGMENT_BIT);
			imageLayoutBindings.get(1).descriptorCount(3);

			colorDescriptorSetLayout = boiler.descriptors.createLayout(stack, colorLayoutBindings, "MatricesLayout");
			imageDescriptorSetLayout = boiler.descriptors.createLayout(stack, imageLayoutBindings, "MatricesImageLayout");
			var sharedPoolBuilder = new SharedDescriptorPoolBuilder(boiler);
			sharedPoolBuilder.request(colorDescriptorSetLayout, NUM_FRAMES_IN_FLIGHT);
			sharedPoolBuilder.request(imageDescriptorSetLayout, NUM_FRAMES_IN_FLIGHT);

			sharedDescriptorPool = sharedPoolBuilder.build("SharedDescriptorPool");
			colorDescriptorSets = sharedDescriptorPool.allocate(colorDescriptorSetLayout, NUM_FRAMES_IN_FLIGHT);
			imageDescriptorSets = sharedDescriptorPool.allocate(imageDescriptorSetLayout, NUM_FRAMES_IN_FLIGHT);

			var descriptorWrites = VkWriteDescriptorSet.calloc(1, stack);
			for (int index = 0; index < NUM_FRAMES_IN_FLIGHT; index++) {
				long[] descriptorSets = { colorDescriptorSets[index], imageDescriptorSets[index] };
				for (long descriptorSet : descriptorSets) {
					boiler.descriptors.writeBuffer(
							stack, descriptorWrites, descriptorSet,
							0, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
							descriptorSet == descriptorSets[0] ? matrixBuffers[index].range() :
									matrixBuffers[index].childRange(0, 128).range()
					);
					vkUpdateDescriptorSets(boiler.vkDevice(), descriptorWrites, null);
				}
				var imageInfo = VkDescriptorImageInfo.calloc(3, stack);
				VkbImage[] images = { getGrassImage.get(), getMountainImage1.get(), getMountainImage2.get() };
				for (int imageIndex = 0; imageIndex < 3; imageIndex++) {
					imageInfo.get(imageIndex).sampler(sampler);
					imageInfo.get(imageIndex).imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
					imageInfo.get(imageIndex).imageView(images[imageIndex].vkImageView());
				}
				boiler.descriptors.writeImage(
						descriptorWrites, imageDescriptorSets[index], 0,
						VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, imageInfo
				);
				descriptorWrites.get(0).dstBinding(1);
				vkUpdateDescriptorSets(boiler.vkDevice(), descriptorWrites, null);
			}

			var pushConstants = VkPushConstantRange.calloc(1, stack);
			pushConstants.stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT);
			pushConstants.offset(0);
			pushConstants.size(8);

			colorPipelineLayout = boiler.pipelines.createLayout(
					pushConstants, "ColorPipelineLayout", colorDescriptorSetLayout.vkDescriptorSetLayout
			);
			imagePipelineLayout = boiler.pipelines.createLayout(
					null, "ImagePipelineLayout", imageDescriptorSetLayout.vkDescriptorSetLayout
			);

			{
				var vertexBindings = VkVertexInputBindingDescription.calloc(1, stack);
				vertexBindings.binding(0);
				vertexBindings.stride(colorVertexSize);
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
						"Xr", "com/github/knokko/boiler/samples/graphics/xr-color.vert.spv",
						"com/github/knokko/boiler/samples/graphics/xr-color.frag.spv"
				);
				pipelineBuilder.ciPipeline.pVertexInputState(ciVertexInput);
				pipelineBuilder.simpleInputAssembly();
				pipelineBuilder.fixedViewport(width, height);
				pipelineBuilder.simpleRasterization(VK_CULL_MODE_BACK_BIT);
				pipelineBuilder.noMultisampling();
				pipelineBuilder.simpleDepth(VK_COMPARE_OP_LESS_OR_EQUAL);
				pipelineBuilder.ciPipeline.layout(colorPipelineLayout);
				pipelineBuilder.noColorBlending(1);
				pipelineBuilder.dynamicRendering(3, depthFormat, VK_FORMAT_UNDEFINED, swapchainFormat);

				colorPipeline = pipelineBuilder.build("ColorPipeline");
			}
			{
				var vertexBindings = VkVertexInputBindingDescription.calloc(1, stack);
				vertexBindings.binding(0);
				vertexBindings.stride(imageVertexSize);
				vertexBindings.inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

				var vertexAttributes = VkVertexInputAttributeDescription.calloc(3, stack);
				var positionAttribute = vertexAttributes.get(0);
				positionAttribute.location(0);
				positionAttribute.binding(0);
				positionAttribute.format(VK_FORMAT_R32G32B32_SFLOAT);
				positionAttribute.offset(0);
				var textureIndexAttribute = vertexAttributes.get(1);
				textureIndexAttribute.location(1);
				textureIndexAttribute.binding(0);
				textureIndexAttribute.format(VK_FORMAT_R32_SINT);
				textureIndexAttribute.offset(12);
				var textureCoordinatesAttribute = vertexAttributes.get(2);
				textureCoordinatesAttribute.location(2);
				textureCoordinatesAttribute.binding(0);
				textureCoordinatesAttribute.format(VK_FORMAT_R32G32_SFLOAT);
				textureCoordinatesAttribute.offset(16);

				var ciVertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack);
				ciVertexInput.sType$Default();
				ciVertexInput.pVertexBindingDescriptions(vertexBindings);
				ciVertexInput.pVertexAttributeDescriptions(vertexAttributes);

				var pipelineBuilder = new GraphicsPipelineBuilder(boiler, stack);
				pipelineBuilder.simpleShaderStages(
						"Xr", "com/github/knokko/boiler/samples/graphics/xr-image.vert.spv",
						"com/github/knokko/boiler/samples/graphics/xr-image.frag.spv"
				);
				pipelineBuilder.ciPipeline.pVertexInputState(ciVertexInput);
				pipelineBuilder.simpleInputAssembly();
				pipelineBuilder.fixedViewport(width, height);
				pipelineBuilder.simpleRasterization(VK_CULL_MODE_NONE);
				pipelineBuilder.noMultisampling();
				pipelineBuilder.simpleDepth(VK_COMPARE_OP_LESS_OR_EQUAL);
				pipelineBuilder.ciPipeline.layout(imagePipelineLayout);
				pipelineBuilder.noColorBlending(1);
				pipelineBuilder.dynamicRendering(3, depthFormat, VK_FORMAT_UNDEFINED, swapchainFormat);

				imagePipeline = pipelineBuilder.build("ImagePipeline");
			}
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

				var colorAttachments = commands.singleColorRenderingAttachment(
						swapchainImageViews[swapchainImageIndex], VK_ATTACHMENT_LOAD_OP_CLEAR,
						VK_ATTACHMENT_STORE_OP_STORE, rgb(255, 0, 0)
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

				vkCmdBeginRendering(commandBuffers[frameIndex], dynamicRenderingInfo);
				vkCmdBindPipeline(commandBuffers[frameIndex], VK_PIPELINE_BIND_POINT_GRAPHICS, colorPipeline);
				commands.bindGraphicsDescriptors(colorPipelineLayout, colorDescriptorSets[frameIndex]);
				commands.bindVertexBuffers(0, colorVertexBuffer.range());
				commands.bindIndexBuffer(colorIndexBuffer.range(), VK_INDEX_TYPE_UINT32);

				var hostMatrixBuffer = matrixBuffers[frameIndex].floatBuffer();
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
						commandBuffers[frameIndex], colorPipelineLayout,
						VK_SHADER_STAGE_FRAGMENT_BIT | VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstants
				);
				vkCmdDrawIndexed(commandBuffers[frameIndex], 3, 1, 0, 0, 0);

				if (leftHandMatrix != null) {
					var giLeftClick = session.prepareSubactionState(stack, handClickAction, pathLeftHand);
					var holdsLeft = session.getBooleanAction(stack, giLeftClick, "left click").currentState();
					pushConstants.put(0, holdsLeft ? 0 : 1);
					pushConstants.put(1, 1);
					vkCmdPushConstants(
							commandBuffers[frameIndex], colorPipelineLayout,
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
							commandBuffers[frameIndex], colorPipelineLayout,
							VK_SHADER_STAGE_FRAGMENT_BIT | VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstants
					);
					vkCmdDrawIndexed(commandBuffers[frameIndex], 12, 1, 3, 0, 0);
				}

				vkCmdBindPipeline(commandBuffers[frameIndex], VK_PIPELINE_BIND_POINT_GRAPHICS, imagePipeline);
				commands.bindGraphicsDescriptors(imagePipelineLayout, imageDescriptorSets[frameIndex]);
				commands.bindVertexBuffers(0, imageVertexBuffer.range());
				commands.bindIndexBuffer(imageIndexBuffer.range(), VK_INDEX_TYPE_UINT32);
				vkCmdDrawIndexed(commandBuffers[frameIndex], 18, 1, 0, 0, 0);

				commands.endDynamicRendering();
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
		vkDestroyPipeline(boiler.vkDevice(), colorPipeline, null);
		vkDestroyPipeline(boiler.vkDevice(), imagePipeline, null);
		vkDestroyPipelineLayout(boiler.vkDevice(), colorPipelineLayout, null);
		vkDestroyPipelineLayout(boiler.vkDevice(), imagePipelineLayout, null);
		colorDescriptorSetLayout.destroy();
		imageDescriptorSetLayout.destroy();
		sharedDescriptorPool.destroy(boiler);
		for (long imageView : swapchainImageViews) {
			vkDestroyImageView(boiler.vkDevice(), imageView, null);
		}

		assertXrSuccess(xrDestroySpace(leftHandSpace), "DestroySpace", "left hand");
		assertXrSuccess(xrDestroySpace(rightHandSpace), "DestroySpace", "right hand");
		assertXrSuccess(xrDestroyAction(handPoseAction), "DestroyAction", "hand pose");
		assertXrSuccess(xrDestroyAction(handClickAction), "DestroyAction", "hand click");
		assertXrSuccess(xrDestroyActionSet(actionSet), "DestroyActionSet", null);

		assertXrSuccess(xrDestroySpace(renderSpace), "DestroySpace", "renderSpace");
		assertXrSuccess(xrDestroySwapchain(swapchain), "DestroySwapchain", null);

		assertXrSuccess(xrDestroySession(session.xrSession), "DestroySession", null);

		vkDestroySampler(boiler.vkDevice(), sampler, null);
		sharedMemoryAllocation.free(boiler);
		boiler.destroyInitialObjects();
	}
}
