package com.github.knokko.boiler.samples;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.builders.WindowBuilder;
import com.github.knokko.boiler.commands.CommandRecorder;
import com.github.knokko.boiler.pipelines.GraphicsPipelineBuilder;
import com.github.knokko.boiler.synchronization.ResourceUsage;
import com.github.knokko.boiler.utilities.ReflectionHelper;
import com.github.knokko.boiler.window.AcquiredImage;
import com.github.knokko.boiler.window.SimpleWindowRenderLoop;
import com.github.knokko.boiler.window.VkbWindow;
import com.github.knokko.boiler.window.WindowEventLoop;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.ktx.*;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK13;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures;
import org.lwjgl.vulkan.VkRenderingAttachmentInfo;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.ktx.KTXVulkan.ktxVulkanDeviceInfo_Construct;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_FIFO_KHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.VK_API_VERSION_1_2;

public class HelloKtx extends SimpleWindowRenderLoop {

	private static void assertKtxSuccess(int result) {
		if (result != KTX.KTX_SUCCESS) throw new RuntimeException("returned " + result + KTX.ktxErrorString(result));
	}

	@SuppressWarnings("resource")
	public static void main(String[] args) {
		var boiler = new BoilerBuilder(
				VK_API_VERSION_1_2, "HelloKtx", 1
		)
				.validation()
				.enableDynamicRendering()
				.addWindow(new WindowBuilder(800, 500, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT))
				.requiredFeatures10(VkPhysicalDeviceFeatures::textureCompressionBC)
				.featurePicker10(((stack, supportedFeatures, toEnable) -> toEnable.textureCompressionBC(true)))
				.build();

		try (var stack = stackPush()) {
			var pTexture = stack.callocPointer(1);
			assertKtxSuccess(KTX.ktxTexture_CreateFromNamedFile(stack.ASCII("test2-bc1.ktx2"), 1, pTexture));
			var ktxTexture = ktxTexture2.create(pTexture.get(0));

			// TODO Using AMD compressonator CLI: .\compressonatorcli.exe -fd bc7 test1.png test1-bc7.ktx2
			int format = KTXVulkan.ktxTexture2_GetVkFormat(ktxTexture);
			System.out.println("format is " + format + ": " + ReflectionHelper.getIntConstantName(VK13.class, format, "VK_FORMAT_", "", "unknown"));

			var uploadCommandPool = boiler.commands.createPool(0, boiler.queueFamilies().graphics().index(), "KtxPool");

			var deviceInfo = ktxVulkanDeviceInfo.calloc(stack);
			assertKtxSuccess(ktxVulkanDeviceInfo_Construct(
					deviceInfo, boiler.vkPhysicalDevice(), boiler.vkDevice(),
					boiler.queueFamilies().graphics().first().vkQueue(),
					uploadCommandPool, null
			));

			var ktxVkTexture = ktxVulkanTexture.calloc(stack);

			assertKtxSuccess(KTXVulkan.ktxTexture2_VkUpload(ktxTexture, deviceInfo, ktxVkTexture));

			System.out.println("size is " + ktxVkTexture.width() + " x " + ktxVkTexture.height());
			KTXVulkan.ktxVulkanTexture_Destruct(ktxVkTexture, boiler.vkDevice(), null);
			KTXVulkan.ktxVulkanDeviceInfo_Destruct(deviceInfo);

			vkDestroyCommandPool(boiler.vkDevice(), uploadCommandPool, null);
		}

//		var eventLoop = new WindowEventLoop();
//		eventLoop.addWindow(new HelloKtx(boiler.window()));
//		eventLoop.runMain();

		boiler.destroyInitialObjects();
	}

	public HelloKtx(VkbWindow window) {
		super(
				window, 1, true, VK_PRESENT_MODE_FIFO_KHR,
				ResourceUsage.COLOR_ATTACHMENT_WRITE, ResourceUsage.COLOR_ATTACHMENT_WRITE
		);
	}

	private long pipelineLayout, graphicsPipeline;

	@Override
	protected void setup(BoilerInstance boiler, MemoryStack stack) {
		super.setup(boiler, stack);
		this.pipelineLayout = boiler.pipelines.createLayout(null, "CompressedLayout");

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
		builder.noColorBlending(1);
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
		vkCmdDraw(recorder.commandBuffer, 6, 1, 0, 0);
		recorder.endDynamicRendering();
	}

	@Override
	protected void cleanUp(BoilerInstance boiler) {
		super.cleanUp(boiler);
		vkDestroyPipeline(boiler.vkDevice(), graphicsPipeline, null);
		vkDestroyPipelineLayout(boiler.vkDevice(), pipelineLayout, null);
	}
}
