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

import static org.lwjgl.system.MemoryUtil.memByteBuffer;
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

	static byte[][] stb__OMatch5 = {
		{ 0, 0 },   { 0, 0 },   { 0, 1 },   { 0, 1 },   { 1, 0 },   { 1, 0 },   { 1, 0 },   { 1, 1 },
		{ 1, 1 },   { 2, 0 },   { 2, 0 },   { 0, 4 },   { 2, 1 },   { 2, 1 },   { 2, 1 },   { 3, 0 },
		{ 3, 0 },   { 3, 0 },   { 3, 1 },   { 1, 5 },   { 3, 2 },   { 3, 2 },   { 4, 0 },   { 4, 0 },
		{ 4, 1 },   { 4, 1 },   { 4, 2 },   { 4, 2 },   { 4, 2 },   { 3, 5 },   { 5, 1 },   { 5, 1 },
		{ 5, 2 },   { 4, 4 },   { 5, 3 },   { 5, 3 },   { 5, 3 },   { 6, 2 },   { 6, 2 },   { 6, 2 },
		{ 6, 3 },   { 5, 5 },   { 6, 4 },   { 6, 4 },   { 4, 8 },   { 7, 3 },   { 7, 3 },   { 7, 3 },
		{ 7, 4 },   { 7, 4 },   { 7, 4 },   { 7, 5 },   { 5, 9 },   { 7, 6 },   { 7, 6 },   { 8, 4 },
		{ 8, 4 },   { 8, 5 },   { 8, 5 },   { 8, 6 },   { 8, 6 },   { 8, 6 },   { 7, 9 },   { 9, 5 },
		{ 9, 5 },   { 9, 6 },   { 8, 8 },   { 9, 7 },   { 9, 7 },   { 9, 7 },   { 10, 6 },  { 10, 6 },
		{ 10, 6 },  { 10, 7 },  { 9, 9 },   { 10, 8 },  { 10, 8 },  { 8, 12 },  { 11, 7 },  { 11, 7 },
		{ 11, 7 },  { 11, 8 },  { 11, 8 },  { 11, 8 },  { 11, 9 },  { 9, 13 },  { 11, 10 }, { 11, 10 },
		{ 12, 8 },  { 12, 8 },  { 12, 9 },  { 12, 9 },  { 12, 10 }, { 12, 10 }, { 12, 10 }, { 11, 13 },
		{ 13, 9 },  { 13, 9 },  { 13, 10 }, { 12, 12 }, { 13, 11 }, { 13, 11 }, { 13, 11 }, { 14, 10 },
		{ 14, 10 }, { 14, 10 }, { 14, 11 }, { 13, 13 }, { 14, 12 }, { 14, 12 }, { 12, 16 }, { 15, 11 },
		{ 15, 11 }, { 15, 11 }, { 15, 12 }, { 15, 12 }, { 15, 12 }, { 15, 13 }, { 13, 17 }, { 15, 14 },
		{ 15, 14 }, { 16, 12 }, { 16, 12 }, { 16, 13 }, { 16, 13 }, { 16, 14 }, { 16, 14 }, { 16, 14 },
		{ 15, 17 }, { 17, 13 }, { 17, 13 }, { 17, 14 }, { 16, 16 }, { 17, 15 }, { 17, 15 }, { 17, 15 },
		{ 18, 14 }, { 18, 14 }, { 18, 14 }, { 18, 15 }, { 17, 17 }, { 18, 16 }, { 18, 16 }, { 16, 20 },
		{ 19, 15 }, { 19, 15 }, { 19, 15 }, { 19, 16 }, { 19, 16 }, { 19, 16 }, { 19, 17 }, { 17, 21 },
		{ 19, 18 }, { 19, 18 }, { 20, 16 }, { 20, 16 }, { 20, 17 }, { 20, 17 }, { 20, 18 }, { 20, 18 },
		{ 20, 18 }, { 19, 21 }, { 21, 17 }, { 21, 17 }, { 21, 18 }, { 20, 20 }, { 21, 19 }, { 21, 19 },
		{ 21, 19 }, { 22, 18 }, { 22, 18 }, { 22, 18 }, { 22, 19 }, { 21, 21 }, { 22, 20 }, { 22, 20 },
		{ 20, 24 }, { 23, 19 }, { 23, 19 }, { 23, 19 }, { 23, 20 }, { 23, 20 }, { 23, 20 }, { 23, 21 },
		{ 21, 25 }, { 23, 22 }, { 23, 22 }, { 24, 20 }, { 24, 20 }, { 24, 21 }, { 24, 21 }, { 24, 22 },
		{ 24, 22 }, { 24, 22 }, { 23, 25 }, { 25, 21 }, { 25, 21 }, { 25, 22 }, { 24, 24 }, { 25, 23 },
		{ 25, 23 }, { 25, 23 }, { 26, 22 }, { 26, 22 }, { 26, 22 }, { 26, 23 }, { 25, 25 }, { 26, 24 },
		{ 26, 24 }, { 24, 28 }, { 27, 23 }, { 27, 23 }, { 27, 23 }, { 27, 24 }, { 27, 24 }, { 27, 24 },
		{ 27, 25 }, { 25, 29 }, { 27, 26 }, { 27, 26 }, { 28, 24 }, { 28, 24 }, { 28, 25 }, { 28, 25 },
		{ 28, 26 }, { 28, 26 }, { 28, 26 }, { 27, 29 }, { 29, 25 }, { 29, 25 }, { 29, 26 }, { 28, 28 },
		{ 29, 27 }, { 29, 27 }, { 29, 27 }, { 30, 26 }, { 30, 26 }, { 30, 26 }, { 30, 27 }, { 29, 29 },
		{ 30, 28 }, { 30, 28 }, { 30, 28 }, { 31, 27 }, { 31, 27 }, { 31, 27 }, { 31, 28 }, { 31, 28 },
		{ 31, 28 }, { 31, 29 }, { 31, 29 }, { 31, 30 }, { 31, 30 }, { 31, 30 }, { 31, 31 }, { 31, 31 }
	};

	static byte[][] stb__OMatch6 = {
		{ 0, 0 },   { 0, 1 },   { 1, 0 },   { 1, 0 },   { 1, 1 },   { 2, 0 },   { 2, 1 },   { 3, 0 },
		{ 3, 0 },   { 3, 1 },   { 4, 0 },   { 4, 0 },   { 4, 1 },   { 5, 0 },   { 5, 1 },   { 6, 0 },
		{ 6, 0 },   { 6, 1 },   { 7, 0 },   { 7, 0 },   { 7, 1 },   { 8, 0 },   { 8, 1 },   { 8, 1 },
		{ 8, 2 },   { 9, 1 },   { 9, 2 },   { 9, 2 },   { 9, 3 },   { 10, 2 },  { 10, 3 },  { 10, 3 },
		{ 10, 4 },  { 11, 3 },  { 11, 4 },  { 11, 4 },  { 11, 5 },  { 12, 4 },  { 12, 5 },  { 12, 5 },
		{ 12, 6 },  { 13, 5 },  { 13, 6 },  { 8, 16 },  { 13, 7 },  { 14, 6 },  { 14, 7 },  { 9, 17 },
		{ 14, 8 },  { 15, 7 },  { 15, 8 },  { 11, 16 }, { 15, 9 },  { 15, 10 }, { 16, 8 },  { 16, 9 },
		{ 16, 10 }, { 15, 13 }, { 17, 9 },  { 17, 10 }, { 17, 11 }, { 15, 16 }, { 18, 10 }, { 18, 11 },
		{ 18, 12 }, { 16, 16 }, { 19, 11 }, { 19, 12 }, { 19, 13 }, { 17, 17 }, { 20, 12 }, { 20, 13 },
		{ 20, 14 }, { 19, 16 }, { 21, 13 }, { 21, 14 }, { 21, 15 }, { 20, 17 }, { 22, 14 }, { 22, 15 },
		{ 25, 10 }, { 22, 16 }, { 23, 15 }, { 23, 16 }, { 26, 11 }, { 23, 17 }, { 24, 16 }, { 24, 17 },
		{ 27, 12 }, { 24, 18 }, { 25, 17 }, { 25, 18 }, { 28, 13 }, { 25, 19 }, { 26, 18 }, { 26, 19 },
		{ 29, 14 }, { 26, 20 }, { 27, 19 }, { 27, 20 }, { 30, 15 }, { 27, 21 }, { 28, 20 }, { 28, 21 },
		{ 28, 21 }, { 28, 22 }, { 29, 21 }, { 29, 22 }, { 24, 32 }, { 29, 23 }, { 30, 22 }, { 30, 23 },
		{ 25, 33 }, { 30, 24 }, { 31, 23 }, { 31, 24 }, { 27, 32 }, { 31, 25 }, { 31, 26 }, { 32, 24 },
		{ 32, 25 }, { 32, 26 }, { 31, 29 }, { 33, 25 }, { 33, 26 }, { 33, 27 }, { 31, 32 }, { 34, 26 },
		{ 34, 27 }, { 34, 28 }, { 32, 32 }, { 35, 27 }, { 35, 28 }, { 35, 29 }, { 33, 33 }, { 36, 28 },
		{ 36, 29 }, { 36, 30 }, { 35, 32 }, { 37, 29 }, { 37, 30 }, { 37, 31 }, { 36, 33 }, { 38, 30 },
		{ 38, 31 }, { 41, 26 }, { 38, 32 }, { 39, 31 }, { 39, 32 }, { 42, 27 }, { 39, 33 }, { 40, 32 },
		{ 40, 33 }, { 43, 28 }, { 40, 34 }, { 41, 33 }, { 41, 34 }, { 44, 29 }, { 41, 35 }, { 42, 34 },
		{ 42, 35 }, { 45, 30 }, { 42, 36 }, { 43, 35 }, { 43, 36 }, { 46, 31 }, { 43, 37 }, { 44, 36 },
		{ 44, 37 }, { 44, 37 }, { 44, 38 }, { 45, 37 }, { 45, 38 }, { 40, 48 }, { 45, 39 }, { 46, 38 },
		{ 46, 39 }, { 41, 49 }, { 46, 40 }, { 47, 39 }, { 47, 40 }, { 43, 48 }, { 47, 41 }, { 47, 42 },
		{ 48, 40 }, { 48, 41 }, { 48, 42 }, { 47, 45 }, { 49, 41 }, { 49, 42 }, { 49, 43 }, { 47, 48 },
		{ 50, 42 }, { 50, 43 }, { 50, 44 }, { 48, 48 }, { 51, 43 }, { 51, 44 }, { 51, 45 }, { 49, 49 },
		{ 52, 44 }, { 52, 45 }, { 52, 46 }, { 51, 48 }, { 53, 45 }, { 53, 46 }, { 53, 47 }, { 52, 49 },
		{ 54, 46 }, { 54, 47 }, { 57, 42 }, { 54, 48 }, { 55, 47 }, { 55, 48 }, { 58, 43 }, { 55, 49 },
		{ 56, 48 }, { 56, 49 }, { 59, 44 }, { 56, 50 }, { 57, 49 }, { 57, 50 }, { 60, 45 }, { 57, 51 },
		{ 58, 50 }, { 58, 51 }, { 61, 46 }, { 58, 52 }, { 59, 51 }, { 59, 52 }, { 62, 47 }, { 59, 53 },
		{ 60, 52 }, { 60, 53 }, { 60, 53 }, { 60, 54 }, { 61, 53 }, { 61, 54 }, { 61, 54 }, { 61, 55 },
		{ 62, 54 }, { 62, 55 }, { 62, 55 }, { 62, 56 }, { 63, 55 }, { 63, 56 }, { 63, 56 }, { 63, 57 },
		{ 63, 58 }, { 63, 59 }, { 63, 59 }, { 63, 60 }, { 63, 61 }, { 63, 62 }, { 63, 62 }, { 63, 63 }
	};

	@Override
	@SuppressWarnings("resource")
	protected void setup(BoilerInstance boiler, MemoryStack stack) {
		super.setup(boiler, stack);

		this.simpleImage = boiler.images.createSimple(
				16, 16, VK_FORMAT_R8G8B8A8_SRGB,
				VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
				VK_IMAGE_ASPECT_COLOR_BIT, "SimpleImage"
		);

		var stagingBuffer = boiler.buffers.createMapped(5 * 16 * 16, VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, "StagingBuffer");
		try {
			var bufferedImage = ImageIO.read(Objects.requireNonNull(HelloKtx.class.getClassLoader().getResourceAsStream(
					"com/github/knokko/boiler/samples/images/test2.png"
			)));
			boiler.buffers.encodeBufferedImageRGBA(stagingBuffer, bufferedImage, 0);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		var betsyPushConstants = VkPushConstantRange.calloc(1, stack);
		betsyPushConstants.offset(0);
		betsyPushConstants.size(8);
		betsyPushConstants.stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);

		var betsyBindings = VkDescriptorSetLayoutBinding.calloc(3, stack);
		for (int index = 0; index < 3; index++) {
			boiler.descriptors.binding(betsyBindings, index, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_SHADER_STAGE_COMPUTE_BIT);
		}

		var betsyDescriptorSetLayout = boiler.descriptors.createLayout(stack, betsyBindings, "BetsyDescriptorSetLayout");
		var computePipelineLayout = boiler.pipelines.createLayout(
				betsyPushConstants, "BetsyPipelineLayout", betsyDescriptorSetLayout.vkDescriptorSetLayout
		);
		var betsyDescriptorPool = betsyDescriptorSetLayout.createPool(1, 0, "BetsyDescriptorPool");
		var betsyDescriptorSet = betsyDescriptorPool.allocate(1)[0];
		var computePipeline = boiler.pipelines.createComputePipeline(
				computePipelineLayout, "com/github/knokko/boiler/samples/betsy/bc1.spv", "BetsyBC1"
		);

		var betsyScratchBuffer = boiler.buffers.createMapped(4096, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, "BetsyScratch");
		var betsyScratchHost = memByteBuffer(betsyScratchBuffer.hostAddress(), (int) betsyScratchBuffer.size());
		for (byte[] bytes5 : stb__OMatch5) {
			betsyScratchHost.putFloat(bytes5[0]).putFloat(bytes5[1]);
		}
		for (byte[] bytes6 : stb__OMatch6) {
			betsyScratchHost.putFloat(bytes6[0]).putFloat(bytes6[1]);
		}

		var betsyWrites = VkWriteDescriptorSet.calloc(3, stack);
		boiler.descriptors.writeBuffer(
				stack, betsyWrites, betsyDescriptorSet, 0,
				VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, betsyScratchBuffer.fullRange()
		);
		boiler.descriptors.writeBuffer(
				stack, betsyWrites, betsyDescriptorSet, 1,
				VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, stagingBuffer.range(0, 4 * 16 * 16)
		);
		boiler.descriptors.writeBuffer(
				stack, betsyWrites, betsyDescriptorSet, 2,
				VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, stagingBuffer.range(4 * 16 * 16, 16 * 16)
		);
		vkUpdateDescriptorSets(boiler.vkDevice(), betsyWrites, null);

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

		var vkb1 = new VkbImage(
				this.ktxVkTextureBC1.image(), this.ktxImageViewBC1, VK_NULL_HANDLE,
				this.ktxVkTextureBC1.width(), this.ktxVkTextureBC1.height(), VK_IMAGE_ASPECT_COLOR_BIT
		);

		var stagingCommandBuffer = boiler.commands.createPrimaryBuffers(stagingCommandPool, 1, "StagingCommandBuffer")[0];
		var recorder = CommandRecorder.begin(stagingCommandBuffer, boiler, stack, "Staging");
		vkCmdBindPipeline(recorder.commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, computePipeline);
		vkCmdBindDescriptorSets(
				recorder.commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE,
				computePipelineLayout, 0, stack.longs(betsyDescriptorSet), null
		);// TODO Create convenience method for this?
		vkCmdPushConstants(
				recorder.commandBuffer, computePipelineLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, stack.ints(2, vkb1.width())
		);
		System.out.println("vkb1 size is " + vkb1.width() + " x " + vkb1.height());
		vkCmdDispatch(recorder.commandBuffer, 4, 4, 1);
		recorder.bufferBarrier(
				stagingBuffer.range(4 * 16 * 16, 16 * 16),
				ResourceUsage.computeBuffer(VK_ACCESS_SHADER_WRITE_BIT),
				ResourceUsage.TRANSFER_SOURCE
		);
		recorder.bufferBarrier(
				stagingBuffer.range(0, 4 * 16 * 16),
				ResourceUsage.computeBuffer(VK_ACCESS_SHADER_READ_BIT),
				ResourceUsage.TRANSFER_SOURCE
		);
		recorder.transitionLayout(this.simpleImage, null, ResourceUsage.TRANSFER_DEST);
		recorder.transitionLayout(vkb1, ResourceUsage.shaderRead(VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT), ResourceUsage.TRANSFER_DEST);

		recorder.copyBufferToImage(this.simpleImage, stagingBuffer.range(0, 4 * 16 * 16));
		recorder.copyBufferToImage(vkb1, stagingBuffer.range(4 * 16 * 16, 16 * 16));
		recorder.transitionLayout(this.simpleImage, ResourceUsage.TRANSFER_DEST, ResourceUsage.shaderRead(VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT));
		recorder.transitionLayout(vkb1, ResourceUsage.TRANSFER_DEST, ResourceUsage.shaderRead(VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT));
		recorder.end();

		var stagingFence = boiler.sync.fenceBank.borrowFence(false, "StagingFence");
		boiler.queueFamilies().graphics().first().submit(stagingCommandBuffer, "Staging", null, stagingFence);
		stagingFence.awaitSignal();
		boiler.sync.fenceBank.returnFence(stagingFence);

		vkDestroyCommandPool(boiler.vkDevice(), stagingCommandPool, null);
		stagingBuffer.destroy(boiler);

		betsyDescriptorPool.destroy();
		betsyDescriptorSetLayout.destroy();
		vkDestroyPipelineLayout(boiler.vkDevice(), computePipelineLayout, null);
		vkDestroyPipeline(boiler.vkDevice(), computePipeline, null);
		betsyScratchBuffer.destroy(boiler);

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
