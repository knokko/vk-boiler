package com.github.knokko.boiler.compute;

import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.commands.SingleTimeCommands;
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

			var fillLayoutBindings = VkDescriptorSetLayoutBinding.calloc(1, stack);
			instance.descriptors.binding(fillLayoutBindings, 0, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_SHADER_STAGE_COMPUTE_BIT);

			var descriptorSetLayout = instance.descriptors.createLayout(
					stack, fillLayoutBindings, "FillBuffer-DescriptorSetLayout"
			);

			var pushConstants = VkPushConstantRange.calloc(1, stack);
			var sizePushConstant = pushConstants.get(0);
			sizePushConstant.stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
			sizePushConstant.offset(0);
			sizePushConstant.size(8);

			long pipelineLayout = instance.pipelines.createLayout(
					pushConstants, "FillBuffer-PipelineLayout", descriptorSetLayout.vkDescriptorSetLayout
			);
			long computePipeline = instance.pipelines.createComputePipeline(
					pipelineLayout, "shaders/fill.comp.spv", "FillBuffer"
			);

			var descriptorPool = descriptorSetLayout.createPool(1, 0, "FillPool");
			long descriptorSet = descriptorPool.allocate(1)[0];

			var descriptorWrites = VkWriteDescriptorSet.calloc(1, stack);
			instance.descriptors.writeBuffer(
					stack, descriptorWrites, descriptorSet,
					0, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, buffer
			);

			vkUpdateDescriptorSets(instance.vkDevice(), descriptorWrites, null);

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

			descriptorPool.destroy();
			vkDestroyPipeline(instance.vkDevice(), computePipeline, null);
			descriptorSetLayout.destroy();
			vkDestroyPipelineLayout(instance.vkDevice(), pipelineLayout, null);
			memory.destroy(instance);
		}

		instance.destroyInitialObjects();
	}
}
