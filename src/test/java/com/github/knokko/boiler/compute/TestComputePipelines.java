package com.github.knokko.boiler.compute;

import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.commands.SingleTimeCommands;
import com.github.knokko.boiler.descriptors.DescriptorCombiner;
import com.github.knokko.boiler.descriptors.DescriptorSetLayoutBuilder;
import com.github.knokko.boiler.descriptors.DescriptorUpdater;
import com.github.knokko.boiler.memory.MemoryCombiner;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.VK_API_VERSION_1_2;

public class TestComputePipelines {

	@Test
	public void testSimpleComputeShader() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_2, "TestSimpleComputeShader", VK_MAKE_VERSION(0, 1, 0)
		).validation().forbidValidationErrors().build();

		try (var stack = stackPush()) {
			int valuesPerInvocation = 16;
			int invocationsPerGroup = 128;
			int groupCount = 1024 * 2;

			var combiner = new MemoryCombiner(instance, "Memory");
			var buffer = combiner.addMappedDeviceLocalBuffer(
					4 * valuesPerInvocation * invocationsPerGroup * groupCount,
					instance.deviceProperties.limits().minStorageBufferOffsetAlignment(),
					VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
			);
			var memory = combiner.build(false);
			var hostBuffer = buffer.intBuffer();

			var builder = new DescriptorSetLayoutBuilder(stack, 1);
			builder.set(0, 0, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_SHADER_STAGE_COMPUTE_BIT);
			var descriptorSetLayout = builder.build(instance, "FullBuffer-DSLayout");

			var pushConstants = VkPushConstantRange.calloc(1, stack);
			var sizePushConstant = pushConstants.get(0);
			sizePushConstant.stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
			sizePushConstant.offset(0);
			sizePushConstant.size(4);

			long pipelineLayout = instance.pipelines.createLayout(
					pushConstants, "FillBuffer-PipelineLayout", descriptorSetLayout.vkDescriptorSetLayout
			);
			long computePipeline = instance.pipelines.createComputePipeline(
					pipelineLayout, "shaders/fill.comp.spv", "FillBuffer"
			);

			var descriptors = new DescriptorCombiner(instance);
			var descriptorSet = descriptors.addMultiple(descriptorSetLayout, 1);
			long vkDescriptorPool = descriptors.build("DescriptorPool");

			var updater = new DescriptorUpdater(stack, 1);
			updater.writeStorageBuffer(0, descriptorSet[0], 0, buffer);
			updater.update(instance);

			SingleTimeCommands.submit(instance, "Filling", recorder -> {
				vkCmdBindPipeline(recorder.commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, computePipeline);
				recorder.bindComputeDescriptors(pipelineLayout, descriptorSet);
				vkCmdPushConstants(
						recorder.commandBuffer, pipelineLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0,
						stack.ints(valuesPerInvocation)
				);
				vkCmdDispatch(recorder.commandBuffer, groupCount, 1, 1);
			}).destroy();

			for (int index = 0; index < hostBuffer.limit(); index++) {
				assertEquals(123456, hostBuffer.get(index));
			}

			vkDestroyDescriptorPool(instance.vkDevice(), vkDescriptorPool, null);
			vkDestroyPipeline(instance.vkDevice(), computePipeline, null);
			vkDestroyDescriptorSetLayout(instance.vkDevice(), descriptorSetLayout.vkDescriptorSetLayout, null);
			vkDestroyPipelineLayout(instance.vkDevice(), pipelineLayout, null);
			memory.destroy(instance);
		}

		instance.destroyInitialObjects();
	}
}
