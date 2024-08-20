package com.github.knokko.boiler.pipelines;

import com.github.knokko.boiler.BoilerInstance;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.io.IOException;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.vulkan.VK10.*;

public class BoilerPipelines {

	private final BoilerInstance instance;

	public BoilerPipelines(BoilerInstance instance) {
		this.instance = instance;
	}

	public long createLayout(
			MemoryStack stack, VkPushConstantRange.Buffer pushConstants, String name, long... descriptorSetLayouts
	) {
		var ciLayout = VkPipelineLayoutCreateInfo.calloc(stack);
		ciLayout.sType$Default();
		ciLayout.flags(0);
		ciLayout.setLayoutCount(1);
		ciLayout.pSetLayouts(stack.longs(descriptorSetLayouts));
		ciLayout.pPushConstantRanges(pushConstants);

		var pLayout = stack.callocLong(1);
		assertVkSuccess(vkCreatePipelineLayout(
				instance.vkDevice(), ciLayout, null, pLayout
		), "CreatePipelineLayout", name);
		long layout = pLayout.get(0);

		instance.debug.name(stack, layout, VK_OBJECT_TYPE_PIPELINE_LAYOUT, name);
		return layout;
	}

	public long createShaderModule(MemoryStack stack, String resourcePath, String name) {
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

		var ciModule = VkShaderModuleCreateInfo.calloc(stack);
		ciModule.sType$Default();
		ciModule.flags(0);
		ciModule.pCode(inputBuffer);

		var pModule = stack.callocLong(1);
		assertVkSuccess(vkCreateShaderModule(
				instance.vkDevice(), ciModule, null, pModule
		), "CreateShaderModule", name);
		long module = pModule.get(0);

		instance.debug.name(stack, module, VK_OBJECT_TYPE_SHADER_MODULE, name);
		memFree(inputBuffer);
		return module;
	}

	public long createComputePipeline(
			MemoryStack stack, long pipelineLayout, String shaderPath, String name
	) {
		long shaderModule = instance.pipelines.createShaderModule(stack, shaderPath, name + "-ShaderModule");

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
				instance.vkDevice(), VK_NULL_HANDLE, ciPipelines, null, pPipelines
		), "CreateComputePipelines", name);
		long pipeline = pPipelines.get(0);

		instance.debug.name(stack, pipeline, VK_OBJECT_TYPE_PIPELINE, name);
		vkDestroyShaderModule(instance.vkDevice(), shaderModule, null);
		return pipeline;
	}
}
