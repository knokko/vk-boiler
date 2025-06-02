package com.github.knokko.boiler.pipelines;

import com.github.knokko.boiler.BoilerInstance;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.util.ArrayList;
import java.util.List;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.vulkan.VK10.*;

public class GraphicsPipelineBuilder {

	/**
	 * The wrapped <i>VkGraphicsPipelineCreateInfo</i>. Feel free to populate the struct when you need to!
	 */
	public final VkGraphicsPipelineCreateInfo ciPipeline;
	private final BoilerInstance instance;
	private final MemoryStack stack;

	private final List<Long> shaderModules = new ArrayList<>();

	/**
	 * The <i>VkPipelineCache</i> that will be used during the <i>build()</i> method. It will be <i>VK_NULL_HANDLE</i>
	 * by default, but you can change it to anything you like.
	 */
	public long pipelineCache = VK_NULL_HANDLE;

	/**
	 * Wraps an existing <i>VkGraphicsPipelineCreateInfo</i> structure. It will <b>not</b> be modified during this
	 * constructor.
	 * @param ciPipeline The createInfo to be wrapped
	 * @param stack The memory stack onto which the methods of this class will allocate structures. It must remain
	 *              valid until the graphics pipeline is created.
	 */
	public GraphicsPipelineBuilder(VkGraphicsPipelineCreateInfo ciPipeline, BoilerInstance instance, MemoryStack stack) {
		this.ciPipeline = ciPipeline;
		this.instance = instance;
		this.stack = stack;
	}

	/**
	 * Allocates and wraps a new zero-initialized <i>VkGraphicsPipelineCreateInfo</i> onto the given stack. The
	 * <i>sType</i> will be set to <i>VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO</i>, and the <i>pNext</i> and
	 * <i>flags</i> will be initialized to 0 (just like all other properties).
	 * @param stack The memory stack onto which the methods of this class will allocate structures. It must remain
	 *              valid until the graphics pipeline is created.
	 */
	public GraphicsPipelineBuilder(BoilerInstance instance, MemoryStack stack) {
		this.ciPipeline = VkGraphicsPipelineCreateInfo.calloc(stack);
		this.ciPipeline.sType$Default();
		this.instance = instance;
		this.stack = stack;
	}

	/**
	 * Populates the <i>stageCount</i> and <i>pStages</i> properties to represent the given shaders. Note that the
	 * <i>simpleShaderStages()</i> method is recommended over this method when you only have a vertex shader and a
	 * fragment shader.
	 * @param shaders All shaders used by this pipeline
	 */
	public void shaderStages(ShaderInfo... shaders) {
		var ciShaderStages = VkPipelineShaderStageCreateInfo.calloc(shaders.length, stack);
		for (int index = 0; index < shaders.length; index++) {
			var shader = shaders[index];
			var ciShader = ciShaderStages.get(index);
			ciShader.sType$Default();
			ciShader.stage(shader.stage());
			ciShader.module(shader.module());
			ciShader.pName(stack.UTF8("main"));
			ciShader.pSpecializationInfo(shader.specialization());
		}

		ciPipeline.stageCount(shaders.length);
		ciPipeline.pStages(ciShaderStages);
	}

	/**
	 * Populates the <i>stageCount</i> and <i>pStages</i> properties such that the graphics pipeline will get a
	 * vertex shader and a fragment shader, whose SPIR-V code can be found using
	 * <i>classLoader.getResourceAsStream(shaderPath + vertex/fragmentFileName)</i>
	 */
	public void simpleShaderStages(String description, String shaderPath, String vertexFileName, String fragmentFileName) {
		long vertexModule = instance.pipelines.createShaderModule(
				shaderPath + vertexFileName, description + "-VertexShader"
		);
		long fragmentModule = instance.pipelines.createShaderModule(
				shaderPath + fragmentFileName, description + "-FragmentShader"
		);
		shaderStages(
				new ShaderInfo(VK_SHADER_STAGE_VERTEX_BIT, vertexModule, null),
				new ShaderInfo(VK_SHADER_STAGE_FRAGMENT_BIT, fragmentModule, null)
		);
		shaderModules.add(vertexModule);
		shaderModules.add(fragmentModule);
	}

	/**
	 * Populates the <i>pVertexInputState</i> property such that the pipeline won't have any vertex input state. This
	 * is useful for pipelines that use vertex pulling or derive their vertices some other way.
	 */
	public void noVertexInput() {
		var vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack);
		vertexInput.sType$Default();
		vertexInput.flags(0);
		vertexInput.pVertexBindingDescriptions(null);
		vertexInput.pVertexAttributeDescriptions(null);

		ciPipeline.pVertexInputState(vertexInput);
	}

	/**
	 * Populates the <i>pInputAssemblyState</i> property with a topology of <i>VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST</i>
	 * and <b>no</b> <i>primitiveRestartEnable</i>.
	 */
	public void simpleInputAssembly() {
		var ciInputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack);
		ciInputAssembly.sType$Default();
		ciInputAssembly.topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
		ciInputAssembly.primitiveRestartEnable(false);

		ciPipeline.pInputAssemblyState(ciInputAssembly);
	}

	/**
	 * Postpones the viewport/scissor size selection to command buffer recording. Note that doing this also requires
	 * you to specify the viewport and scissor in the <i>pDynamicState</i>
	 * (e.g. <i>dynamicStates(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR)</i>)
	 * @param numViewports The number of viewports (usually 1)
	 */
	public void dynamicViewports(int numViewports) {
		var ciViewport = VkPipelineViewportStateCreateInfo.calloc(stack);
		ciViewport.sType$Default();
		ciViewport.viewportCount(numViewports);
		ciViewport.pViewports(null);
		ciViewport.scissorCount(numViewports);
		ciViewport.pScissors(null);

		ciPipeline.pViewportState(ciViewport);
	}

	/**
	 * Sets the size of the viewport and scissor to the given width and height. This method only supports 1 viewport
	 * (which is usually the case).
	 * @param width The width of the viewport, in pixels
	 * @param height The height of the viewport, in pixels
	 */
	public void fixedViewport(int width, int height) {
		var viewportSize = VkViewport.calloc(1, stack);
		viewportSize.x(0f);
		viewportSize.y(0f);
		viewportSize.width(width);
		viewportSize.height(height);
		viewportSize.minDepth(0f);
		viewportSize.maxDepth(1f);

		var scissorSize = VkRect2D.calloc(1, stack);
		scissorSize.offset().set(0, 0);
		scissorSize.extent().set(width, height);

		var ciViewport = VkPipelineViewportStateCreateInfo.calloc(stack);
		ciViewport.sType$Default();
		ciViewport.viewportCount(1);
		ciViewport.pViewports(viewportSize);
		ciViewport.scissorCount(1);
		ciViewport.pScissors(scissorSize);

		ciPipeline.pViewportState(ciViewport);
	}

	/**
	 * Chooses a 'simple' pipeline rasterization state with the given cullMode:
	 * <ul>
	 *     <li>depthClampEnable = false</li>
	 *     <li>rasterizerDiscardEnable = false</li>
	 *     <li>polygonMode = VK_POLYGON_MODE_FILL</li>
	 *     <li>frontFace = VK_FRONT_FACE_COUNTER_CLOCKWISE</li>
	 *     <li>depthBiasEnable = false</li>
	 *     <li>lineWidth = 1</li>
	 * </ul>
	 * @param cullMode The cull mode (e.g. <i>VK_CULL_MODE_BACK_BIT</i>)
	 */
	public void simpleRasterization(int cullMode) {
		var ciRaster = VkPipelineRasterizationStateCreateInfo.calloc(stack);
		ciRaster.sType$Default();
		ciRaster.depthClampEnable(false);
		ciRaster.rasterizerDiscardEnable(false);
		ciRaster.polygonMode(VK_POLYGON_MODE_FILL);
		ciRaster.cullMode(cullMode);
		ciRaster.frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE);
		ciRaster.depthBiasEnable(false);
		ciRaster.lineWidth(1f);

		ciPipeline.pRasterizationState(ciRaster);
	}

	/**
	 * Populates the <i>pMultisampleState</i> such that the pipeline won't use multisampling.
	 */
	public void noMultisampling() {
		var ciMultisample = VkPipelineMultisampleStateCreateInfo.calloc(stack);
		ciMultisample.sType$Default();
		ciMultisample.rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
		ciMultisample.sampleShadingEnable(false);

		ciPipeline.pMultisampleState(ciMultisample);
	}

	/**
	 * Populates the <i>pDepthStencilState</i> such that the pipeline won't have a depth test, nor a stencil test.
	 */
	public void noDepthStencil() {
		var ciDepthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack);
		ciDepthStencil.sType$Default();
		ciDepthStencil.depthTestEnable(false);
		ciDepthStencil.depthWriteEnable(false);
		ciDepthStencil.depthCompareOp(VK_COMPARE_OP_ALWAYS);
		ciDepthStencil.depthBoundsTestEnable(false);
		ciDepthStencil.stencilTestEnable(false);

		ciPipeline.pDepthStencilState(ciDepthStencil);
	}

	/**
	 * Populates the <i>pDepthStencilState</i> such that the pipeline will <b>not</b> use a stencil test, and use the
	 * following depth test:
	 * <ul>
	 *     <li>depthTestEnable = true</li>
	 *     <li>depthWriteEnable = true</li>
	 *     <li>depthBoundsTestEnable = false</li>
	 * </ul>
	 * @param compareOp the <i>depthCompareOp</i>, for instance <i>VK_COMPARE_OP_LESS</i>
	 */
	public void simpleDepth(int compareOp) {
		var ciDepthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack);
		ciDepthStencil.sType$Default();
		ciDepthStencil.depthTestEnable(true);
		ciDepthStencil.depthWriteEnable(true);
		ciDepthStencil.depthCompareOp(compareOp);
		ciDepthStencil.depthBoundsTestEnable(false);
		ciDepthStencil.stencilTestEnable(false);

		ciPipeline.pDepthStencilState(ciDepthStencil);
	}

	/**
	 * Populates the <i>pColorBlendState</i> such that the pipeline won't have any color blending or logic OP.
	 * All the R, G, B, and A components will be written as-is.
	 * @param attachmentCount The number of color attachments
	 */
	public void noColorBlending(int attachmentCount) {
		var pAttachments = VkPipelineColorBlendAttachmentState.calloc(attachmentCount, stack);
		for (int index = 0; index < attachmentCount; index++) {
			var attachment = pAttachments.get(index);
			attachment.blendEnable(false);
			attachment.colorWriteMask(
					VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT |
							VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT
			);
		}

		var ciColorBlend = VkPipelineColorBlendStateCreateInfo.calloc(stack);
		ciColorBlend.sType$Default();
		ciColorBlend.logicOpEnable(false);
		ciColorBlend.attachmentCount(attachmentCount);
		ciColorBlend.pAttachments(pAttachments);

		ciPipeline.pColorBlendState(ciColorBlend);
	}

	/**
	 * Populates the <i>pColorBlendState</i> such that the pipeline will have 'standard' color blending:
	 * <ul>
	 *     <li>blendedColor = newAlpha * newColor + (1 - newAlpha) * oldColor</li>
	 *     <li>blendedAlpha = max(oldAlpha, newAlpha)</li>
	 * </ul>
	 * @param attachmentCount The number of color attachments
	 */
	public void simpleColorBlending(int attachmentCount) {
		var attachments = VkPipelineColorBlendAttachmentState.calloc(attachmentCount, stack);
		for (int index = 0; index < attachmentCount; index++) {
			var attachment = attachments.get(index);
			attachment.blendEnable(true);
			attachment.srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA);
			attachment.dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA);
			attachment.colorBlendOp(VK_BLEND_OP_ADD);
			attachment.srcAlphaBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA);
			attachment.dstAlphaBlendFactor(VK_BLEND_FACTOR_DST_ALPHA);
			attachment.alphaBlendOp(VK_BLEND_OP_MAX);
			attachment.colorWriteMask(
					VK_COLOR_COMPONENT_R_BIT |
							VK_COLOR_COMPONENT_G_BIT |
							VK_COLOR_COMPONENT_B_BIT |
							VK_COLOR_COMPONENT_A_BIT
			);
		}

		var ciColorBlend = VkPipelineColorBlendStateCreateInfo.calloc(stack);
		ciColorBlend.sType$Default();
		ciColorBlend.logicOpEnable(false);
		ciColorBlend.pAttachments(attachments);

		ciPipeline.pColorBlendState(ciColorBlend);
	}

	/**
	 * Populates the <i>pDynamicState</i> of the pipeline with the given <i>dynamicStates</i>.
	 */
	public void dynamicStates(int... dynamicStates) {
		var ciDynamic = VkPipelineDynamicStateCreateInfo.calloc(stack);
		ciDynamic.sType$Default();
		ciDynamic.pDynamicStates(stack.ints(dynamicStates));

		ciPipeline.pDynamicState(ciDynamic);
	}

	/**
	 * Chains a <i>VkPipelineRenderingCreateInfo</i> to the <i>pNext</i>
	 * @param viewMask The view mask when multiview rendering is used. Can be 0 when you don't use multiview rendering
	 * @param depthFormat The <i>depthAttachmentFormat</i>
	 * @param stencilFormat The <i>stencilAttachmentFormat</i>
	 * @param colorFormats The <i>pColorAttachmentFormats</i>
	 */
	public void dynamicRendering(int viewMask, int depthFormat, int stencilFormat, int... colorFormats) {
		var ciRendering = VkPipelineRenderingCreateInfo.calloc(stack);
		ciRendering.sType$Default();
		ciRendering.viewMask(viewMask);
		ciRendering.colorAttachmentCount(colorFormats.length);
		ciRendering.pColorAttachmentFormats(stack.ints(colorFormats));
		ciRendering.depthAttachmentFormat(depthFormat);
		ciRendering.stencilAttachmentFormat(stencilFormat);

		ciPipeline.renderPass(VK_NULL_HANDLE);
		ciPipeline.pNext(ciRendering);
	}

	public long build(String name) {
		var pPipeline = stack.callocLong(1);
		assertVkSuccess(vkCreateGraphicsPipelines(
				instance.vkDevice(), pipelineCache,
				VkGraphicsPipelineCreateInfo.create(ciPipeline.address(), 1),
				null, pPipeline
		), "CreateGraphicsPipelines", name);
		long pipeline = pPipeline.get(0);
		instance.debug.name(stack, pipeline, VK_OBJECT_TYPE_PIPELINE, name);

		for (long module : shaderModules) {
			vkDestroyShaderModule(instance.vkDevice(), module, null);
		}

		return pipeline;
	}
}
