package com.github.knokko.boiler.samples;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.builders.WindowBuilder;
import com.github.knokko.boiler.commands.CommandRecorder;
import com.github.knokko.boiler.descriptors.HomogeneousDescriptorPool;
import com.github.knokko.boiler.descriptors.VkbDescriptorSetLayout;
import com.github.knokko.boiler.images.VkbImage;
import com.github.knokko.boiler.pipelines.GraphicsPipelineBuilder;
import com.github.knokko.boiler.synchronization.ResourceUsage;
import com.github.knokko.boiler.window.AcquiredImage;
import com.github.knokko.boiler.window.SimpleWindowRenderLoop;
import com.github.knokko.boiler.window.VkbWindow;
import com.github.knokko.boiler.window.WindowEventLoop;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.ktx.*;
import org.lwjgl.vulkan.*;

import javax.imageio.ImageIO;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.util.Objects;

import static org.lwjgl.util.ktx.KTXVulkan.*;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_FIFO_KHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.VK_API_VERSION_1_2;

public class HelloKtx extends SimpleWindowRenderLoop {

	private static void assertKtxSuccess(int result) {
		if (result != KTX.KTX_SUCCESS) throw new RuntimeException("returned " + result + KTX.ktxErrorString(result));
	}

	public static void main(String[] args) throws IOException {
		var boiler = new BoilerBuilder(
				VK_API_VERSION_1_2, "HelloKtx", 1
		)
				.validation()
				.enableDynamicRendering()
				.addWindow(new WindowBuilder(800, 500, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT))
				.requiredFeatures10(VkPhysicalDeviceFeatures::textureCompressionBC)
				.featurePicker10(((stack, supportedFeatures, toEnable) -> toEnable.textureCompressionBC(true)))
				.build();

//		byte[] ddsBytes = Files.readAllBytes(new File("test2.dds").toPath());
//		IntBuffer dds = ByteBuffer.wrap(ddsBytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
//		for (int counter = 0; counter < 3; counter++) dds.get(); // Skip some header stuff
//		System.out.println("size is " + dds.get() + " x " + dds.get());
//		System.out.println("cool size " + dds.get());
//		System.out.println("depth is " + dds.get());
//		System.out.println("mips is " + dds.get());
//		for (int counter = 0; counter < 11; counter++) dds.get(); // Mostly reserved stuff
//		System.out.println("pixel format:");
//		dds.get();
//		System.out.println("  flags = " + dds.get());
//		int fourCC = dds.get();
//		System.out.println("  4-character-code is " + (char) (byte) fourCC + (char) (byte) (fourCC >> 8) + (char) (byte) (fourCC >> 16) + (char) (byte) (fourCC >> 24));
//		for (int counter = 0; counter < 5; counter++) System.out.println(dds.get());
//		System.out.println("caps:");
//		for (int counter = 0; counter < 4; counter++) System.out.println(dds.get());
//		dds.get(); // reserved
//		System.out.println("remaining is " + dds.remaining());
//		while (dds.hasRemaining()) System.out.println(dds.get());

		var eventLoop = new WindowEventLoop();
		eventLoop.addWindow(new HelloKtx(boiler.window()));
		eventLoop.runMain();

		boiler.destroyInitialObjects();
	}

	public HelloKtx(VkbWindow window) {
		super(
				window, 1, true, VK_PRESENT_MODE_FIFO_KHR,
				ResourceUsage.COLOR_ATTACHMENT_WRITE, ResourceUsage.COLOR_ATTACHMENT_WRITE
		);
	}

	private VkbImage simpleImage;
	private ktxVulkanTexture ktxVkTextureBC1, ktxVkTextureBC3, ktxVkTextureBC7;
	private long ktxImageViewBC1, ktxImageViewBC3, ktxImageViewBC7;

	private long sampler;
	private VkbDescriptorSetLayout descriptorSetLayout;
	private HomogeneousDescriptorPool descriptorPool;
	private long descriptorSet;
	private long pipelineLayout, graphicsPipeline;

	@Override
	@SuppressWarnings("resource")
	protected void setup(BoilerInstance boiler, MemoryStack stack) {
		super.setup(boiler, stack);

		this.simpleImage = boiler.images.createSimple(
				16, 16, VK_FORMAT_R8G8B8A8_SRGB,
				VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
				VK_IMAGE_ASPECT_COLOR_BIT, "SimpleImage"
		);

		var stagingBuffer = boiler.buffers.createMapped(4 * 16 * 16, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, "StagingBuffer");
		try {
			var bufferedImage = ImageIO.read(Objects.requireNonNull(HelloKtx.class.getClassLoader().getResourceAsStream(
					"com/github/knokko/boiler/samples/images/test2.png"
			)));
			boiler.buffers.encodeBufferedImageRGBA(stagingBuffer, bufferedImage, 0);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		var stagingCommandPool = boiler.commands.createPool(
				VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT,
				boiler.queueFamilies().graphics().index(), "StagingCommandPool"
		);

		var pKtxTexture = stack.callocPointer(1);
		assertKtxSuccess(KTX.ktxTexture_CreateFromNamedFile(stack.ASCII("test2-bc1.ktx2"), 1, pKtxTexture));
		var ktxTextureBC1 = ktxTexture2.create(pKtxTexture.get(0));
		assertKtxSuccess(KTX.ktxTexture_CreateFromNamedFile(stack.ASCII("test2-bc3.ktx2"), 1, pKtxTexture));
		var ktxTextureBC3 = ktxTexture2.create(pKtxTexture.get(0));
		assertKtxSuccess(KTX.ktxTexture_CreateFromNamedFile(stack.ASCII("test2-bc7.ktx2"), 1, pKtxTexture));
		var ktxTextureBC7 = ktxTexture2.create(pKtxTexture.get(0));

		// TODO Using AMD compressonator CLI: .\compressonatorcli.exe -fd bc7 test1.png test1-bc7.ktx2
		var ktxDeviceInfo = ktxVulkanDeviceInfo.calloc(stack);
		assertKtxSuccess(ktxVulkanDeviceInfo_Construct(
				ktxDeviceInfo, boiler.vkPhysicalDevice(), boiler.vkDevice(),
				boiler.queueFamilies().graphics().first().vkQueue(),
				stagingCommandPool, null
		));

		this.ktxVkTextureBC1 = ktxVulkanTexture.calloc();
		this.ktxVkTextureBC3 = ktxVulkanTexture.calloc();
		this.ktxVkTextureBC7 = ktxVulkanTexture.calloc();

		assertKtxSuccess(ktxTexture2_VkUpload(ktxTextureBC1, ktxDeviceInfo, ktxVkTextureBC1));
		assertKtxSuccess(ktxTexture2_VkUpload(ktxTextureBC3, ktxDeviceInfo, ktxVkTextureBC3));
		assertKtxSuccess(ktxTexture2_VkUpload(ktxTextureBC7, ktxDeviceInfo, ktxVkTextureBC7));

		ktxVulkanDeviceInfo_Destruct(ktxDeviceInfo);

		this.ktxImageViewBC1 = boiler.images.createSimpleView(
				this.ktxVkTextureBC1.image(), this.ktxVkTextureBC1.imageFormat(), VK_IMAGE_ASPECT_COLOR_BIT, "KtxImageViewBC1"
		);
		this.ktxImageViewBC3 = boiler.images.createSimpleView(
				this.ktxVkTextureBC3.image(), this.ktxVkTextureBC3.imageFormat(), VK_IMAGE_ASPECT_COLOR_BIT, "KtxImageViewBC3"
		);
		this.ktxImageViewBC7 = boiler.images.createSimpleView(
				this.ktxVkTextureBC7.image(), this.ktxVkTextureBC7.imageFormat(), VK_IMAGE_ASPECT_COLOR_BIT, "KtxImageViewBC7"
		);

		var stagingCommandBuffer = boiler.commands.createPrimaryBuffers(stagingCommandPool, 1, "StagingCommandBuffer")[0];
		var recorder = CommandRecorder.begin(stagingCommandBuffer, boiler, stack, "Staging");
		recorder.transitionLayout(this.simpleImage, null, ResourceUsage.TRANSFER_DEST);
		recorder.copyBufferToImage(this.simpleImage, stagingBuffer.vkBuffer());// TODO Use buffer range instead?
		recorder.transitionLayout(this.simpleImage, ResourceUsage.TRANSFER_DEST, ResourceUsage.shaderRead(VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT));
		recorder.end();

		var stagingFence = boiler.sync.fenceBank.borrowFence(false, "StagingFence");
		boiler.queueFamilies().graphics().first().submit(stagingCommandBuffer, "Staging", null, stagingFence);
		stagingFence.awaitSignal();
		boiler.sync.fenceBank.returnFence(stagingFence);

		vkDestroyCommandPool(boiler.vkDevice(), stagingCommandPool, null);
		stagingBuffer.destroy(boiler);

		this.sampler = boiler.images.createSimpleSampler(
				VK_FILTER_NEAREST, VK_SAMPLER_MIPMAP_MODE_NEAREST,
				VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER, "TheSampler"
		);

		var descriptorBindings = VkDescriptorSetLayoutBinding.calloc(2, stack);
		boiler.descriptors.binding(descriptorBindings, 0, VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE, VK_SHADER_STAGE_FRAGMENT_BIT);
		descriptorBindings.get(0).descriptorCount(4);
		boiler.descriptors.binding(descriptorBindings, 1, VK_DESCRIPTOR_TYPE_SAMPLER, VK_SHADER_STAGE_FRAGMENT_BIT);

		this.descriptorSetLayout = boiler.descriptors.createLayout(stack, descriptorBindings, "ImagesLayout");
		this.descriptorPool = descriptorSetLayout.createPool(1, 0, "ImagesPool");
		this.descriptorSet = descriptorPool.allocate(1)[0];

		var writeImages = VkDescriptorImageInfo.calloc(4, stack);
		for (int index = 0; index < 4; index++) {
			writeImages.get(index).imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
		}
		writeImages.get(0).imageView(this.simpleImage.vkImageView());
		writeImages.get(1).imageView(ktxImageViewBC1);
		writeImages.get(2).imageView(ktxImageViewBC3);
		writeImages.get(3).imageView(ktxImageViewBC7);

		var writeSamplers = VkDescriptorImageInfo.calloc(1, stack);
		writeSamplers.sampler(sampler);

		var descriptorWrites = VkWriteDescriptorSet.calloc(2, stack);
		boiler.descriptors.writeImage(descriptorWrites, descriptorSet, 0, VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE, writeImages);
		boiler.descriptors.writeImage(descriptorWrites, descriptorSet, 1, VK_DESCRIPTOR_TYPE_SAMPLER, writeSamplers);

		vkUpdateDescriptorSets(boiler.vkDevice(), descriptorWrites, null);

		var pushConstants = VkPushConstantRange.calloc(2, stack);
		var vertexPushConstants = pushConstants.get(0);
		vertexPushConstants.offset(0);
		vertexPushConstants.size(8);
		vertexPushConstants.stageFlags(VK_SHADER_STAGE_VERTEX_BIT);
		var fragmentPushConstants = pushConstants.get(1);
		fragmentPushConstants.offset(vertexPushConstants.size());
		fragmentPushConstants.size(4);
		fragmentPushConstants.stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
		this.pipelineLayout = boiler.pipelines.createLayout(
				pushConstants, "CompressedLayout", descriptorSetLayout.vkDescriptorSetLayout
		);

		var builder = new GraphicsPipelineBuilder(boiler, stack);
		builder.simpleShaderStages(
				"CompressedPipeline",
				"com/github/knokko/boiler/samples/graphics/ktx.vert.spv",
				"com/github/knokko/boiler/samples/graphics/ktx.frag.spv"
		);
		builder.noVertexInput();
		builder.simpleInputAssembly();
		builder.dynamicViewports(1);
		builder.simpleRasterization(VK_CULL_MODE_NONE);
		builder.noMultisampling();
		builder.noDepthStencil();
		builder.simpleColorBlending(1);
		builder.dynamicStates(VK_DYNAMIC_STATE_SCISSOR, VK_DYNAMIC_STATE_VIEWPORT);
		builder.ciPipeline.layout(pipelineLayout);
		builder.dynamicRendering(0, VK_FORMAT_UNDEFINED, VK_FORMAT_UNDEFINED, window.surfaceFormat);
		this.graphicsPipeline = builder.build("CompressedPipeline");
    }

	@Override
	protected void recordFrame(
			MemoryStack stack, int frameIndex, CommandRecorder recorder,
			AcquiredImage acquiredImage, BoilerInstance instance
	) {
		var colorAttachments = VkRenderingAttachmentInfo.calloc(1, stack);
		recorder.simpleColorRenderingAttachment(
				colorAttachments.get(0), acquiredImage.image().vkImageView(),
				VK_ATTACHMENT_LOAD_OP_CLEAR, VK_ATTACHMENT_STORE_OP_STORE,
				1f, 0f, 0f, 1f
		);
		recorder.beginSimpleDynamicRendering(
				acquiredImage.width(), acquiredImage.height(),
				colorAttachments, null, null
		);
		vkCmdBindPipeline(recorder.commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicsPipeline);
		recorder.dynamicViewportAndScissor(acquiredImage.width(), acquiredImage.height());
		vkCmdBindDescriptorSets(
				recorder.commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipelineLayout,
				0, stack.longs(descriptorSet), null
		);

		var vertexPushConstants = stack.callocFloat(2);
		var fragmentPushConstants = stack.callocInt(1);
		drawQuad(recorder.commandBuffer, vertexPushConstants, fragmentPushConstants, -0.9f, -0.9f, 0);
		drawQuad(recorder.commandBuffer, vertexPushConstants, fragmentPushConstants, 0.1f, -0.9f, 1);
		drawQuad(recorder.commandBuffer, vertexPushConstants, fragmentPushConstants, 0.1f, 0.1f, 2);
		drawQuad(recorder.commandBuffer, vertexPushConstants, fragmentPushConstants, -0.9f, 0.1f, 3);
		recorder.endDynamicRendering();
	}

	private void drawQuad(
			VkCommandBuffer commandBuffer, FloatBuffer vertexPushConstants, IntBuffer fragmentPushConstants,
			float offsetX, float offsetY, int imageIndex
	) {
		vertexPushConstants.put(0, offsetX).put(1, offsetY);
		vkCmdPushConstants(commandBuffer, pipelineLayout, VK_SHADER_STAGE_VERTEX_BIT, 0, vertexPushConstants);

		fragmentPushConstants.put(0, imageIndex);
		vkCmdPushConstants(commandBuffer, pipelineLayout, VK_SHADER_STAGE_FRAGMENT_BIT, 8, fragmentPushConstants);

		vkCmdDraw(commandBuffer, 6, 1, 0, 0);
	}

	@Override
	protected void cleanUp(BoilerInstance boiler) {
		super.cleanUp(boiler);
		descriptorPool.destroy();
		descriptorSetLayout.destroy();
		simpleImage.destroy(boiler);
		vkDestroyImageView(boiler.vkDevice(), ktxImageViewBC1, null);
		vkDestroyImageView(boiler.vkDevice(), ktxImageViewBC3, null);
		vkDestroyImageView(boiler.vkDevice(), ktxImageViewBC7, null);
		ktxVulkanTexture_Destruct(ktxVkTextureBC1, boiler.vkDevice(), null);
		ktxVulkanTexture_Destruct(ktxVkTextureBC3, boiler.vkDevice(), null);
		ktxVulkanTexture_Destruct(ktxVkTextureBC7, boiler.vkDevice(), null);
		ktxVkTextureBC1.free();
		ktxVkTextureBC3.free();
		ktxVkTextureBC7.free();
		vkDestroySampler(boiler.vkDevice(), sampler, null);
		vkDestroyPipeline(boiler.vkDevice(), graphicsPipeline, null);
		vkDestroyPipelineLayout(boiler.vkDevice(), pipelineLayout, null);
	}
}
