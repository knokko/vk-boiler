package com.github.knokko.boiler.pipelines;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.memory.callbacks.CallbackUserData;
import org.lwjgl.vulkan.*;

import java.io.IOException;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.vulkan.VK10.*;

public class BoilerPipelines {

	private final BoilerInstance instance;

	/**
	 * This constructor is meant for internal use only. You should use <i>boilerInstance.pipelines</i> instead.
	 */
	public BoilerPipelines(BoilerInstance instance) {
		this.instance = instance;
	}

	/**
	 * Uses <i>vkCreatePipelineLayout</i> to create a pipeline layout with the given push constants and descriptor set
	 * layout(s)
	 * @param pushConstants The <i>VkPushConstantRange</i>s of the pipeline layout, or null when it doesn't use push
	 *                      constants
	 * @param name The debug name of the pipeline layout (when <i>VK_EXT_debug_utils</i> is enabled)
	 * @param descriptorSetLayouts The <i>VkDescriptorSetLayout</i>s that should be passed to
	 *                             <i>VkPipelineLayoutCreateInfo.pSetLayouts</i>
	 * @return The created <i>VkPipelineLayout</i> handle
	 */
	public long createLayout(
			VkPushConstantRange.Buffer pushConstants, String name, long... descriptorSetLayouts
	) {
		try (var stack = stackPush()) {
			var ciLayout = VkPipelineLayoutCreateInfo.calloc(stack);
			ciLayout.sType$Default();
			ciLayout.flags(0);
			ciLayout.setLayoutCount(1);
			ciLayout.pSetLayouts(stack.longs(descriptorSetLayouts));
			ciLayout.pPushConstantRanges(pushConstants);

			var pLayout = stack.callocLong(1);
			assertVkSuccess(vkCreatePipelineLayout(
					instance.vkDevice(), ciLayout, CallbackUserData.PIPELINE_LAYOUT.put(stack, instance), pLayout
			), "CreatePipelineLayout", name);
			long layout = pLayout.get(0);

			instance.debug.name(stack, layout, VK_OBJECT_TYPE_PIPELINE_LAYOUT, name);
			return layout;
		}
	}

	/**
	 * Creates a <i>VkShaderModule</i> using the SPIR-V from
	 * <i>BoilerPipelines.class.getClassLoader().getResourceAsStream(resourcePath)</i>.
	 * @param resourcePath The class loader resource path
	 * @param name The debug name of the shader module (when <i>VK_EXT_debug_utils</i> is enabled)
	 * @return The created <i>VkShaderModule</i> handle
	 */
	public long createShaderModule(String resourcePath, String name) {
		var input = BoilerPipelines.class.getClassLoader().getResourceAsStream(resourcePath);
		if (input == null) throw new IllegalArgumentException("Can't find resource: " + resourcePath);
		byte[] inputArray;
		try {
			inputArray = input.readAllBytes();
			input.close();
		} catch (IOException shouldNotHappen) {
			throw new RuntimeException(shouldNotHappen);
		}

		var inputBuffer = memAlloc(inputArray.length);
		inputBuffer.put(0, inputArray);

		long module;
		try (var stack = stackPush()) {
			var ciModule = VkShaderModuleCreateInfo.calloc(stack);
			ciModule.sType$Default();
			ciModule.flags(0);
			ciModule.pCode(inputBuffer);

			var pModule = stack.callocLong(1);
			assertVkSuccess(vkCreateShaderModule(
					instance.vkDevice(), ciModule, CallbackUserData.SHADER_MODULE.put(stack, instance), pModule
			), "CreateShaderModule", name);
			module = pModule.get(0);

			instance.debug.name(stack, module, VK_OBJECT_TYPE_SHADER_MODULE, name);
		}
		memFree(inputBuffer);
		return module;
	}

	/**
	 * Creates a compute pipeline using the SPIR-V from
	 * <i>BoilerPipelines.class.getClassLoader().getResourceAsStream(shaderPath)</i>.
	 * @param pipelineLayout The <i>VkPipelineLayout</i> for the compute pipeline
	 * @param shaderPath The class loader resource path
	 * @param name The debug name of the compute pipeline (when <i>VK_EXT_debug_utils</i> is enabled)
	 * @return The created <i>VkPipeline</i> handle
	 */
	public long createComputePipeline(
			long pipelineLayout, String shaderPath, String name
	) {
		long shaderModule = instance.pipelines.createShaderModule(shaderPath, name + "-ShaderModule");

		long pipeline;
		try (var stack = stackPush()) {
			var ciShaderStage = VkPipelineShaderStageCreateInfo.calloc(stack);
			ciShaderStage.sType$Default();
			ciShaderStage.flags(0);
			ciShaderStage.stage(VK_SHADER_STAGE_COMPUTE_BIT);
			ciShaderStage.module(shaderModule);
			ciShaderStage.pName(stack.UTF8("main"));

			var ciPipelines = VkComputePipelineCreateInfo.calloc(1, stack);
			var ciPipeline = ciPipelines.get(0);
			ciPipeline.sType$Default();
			ciPipeline.stage(ciShaderStage);
			ciPipeline.layout(pipelineLayout);

			var pPipelines = stack.callocLong(1);
			assertVkSuccess(vkCreateComputePipelines(
					instance.vkDevice(), VK_NULL_HANDLE, ciPipelines,
					CallbackUserData.PIPELINE.put(stack, instance), pPipelines
			), "CreateComputePipelines", name);
			pipeline = pPipelines.get(0);

			instance.debug.name(stack, pipeline, VK_OBJECT_TYPE_PIPELINE, name);
			vkDestroyShaderModule(instance.vkDevice(), shaderModule, CallbackUserData.SHADER_MODULE.put(stack, instance));
		}
		return pipeline;
	}
}
