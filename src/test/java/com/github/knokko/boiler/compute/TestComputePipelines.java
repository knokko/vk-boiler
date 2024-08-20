package com.github.knokko.boiler.compute;

import com.github.knokko.boiler.builder.BoilerBuilder;
import com.github.knokko.boiler.sync.WaitSemaphore;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.*;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.util.vma.Vma.vmaDestroyBuffer;
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

			var buffer = instance.buffers.createMapped(
					4 * valuesPerInvocation * invocationsPerGroup * groupCount,
					VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, "Filled"
			);
			var hostBuffer = memIntBuffer(buffer.hostAddress(), valuesPerInvocation * invocationsPerGroup * groupCount);

			var fillLayoutBindings = VkDescriptorSetLayoutBinding.calloc(1, stack);
			var fillBufferLayoutBinding = fillLayoutBindings.get(0);
			fillBufferLayoutBinding.binding(0);
			fillBufferLayoutBinding.descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER);
			fillBufferLayoutBinding.descriptorCount(1);
			fillBufferLayoutBinding.stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);

			var descriptorSetLayout = instance.descriptors.createLayout(
					stack, fillLayoutBindings, "FillBuffer-DescriptorSetLayout"
			);

			var pushConstants = VkPushConstantRange.calloc(1, stack);
			var sizePushConstant = pushConstants.get(0);
			sizePushConstant.stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
			sizePushConstant.offset(0);
			sizePushConstant.size(8);

			long pipelineLayout = instance.pipelines.createLayout(
					stack, pushConstants, "FillBuffer-PipelineLayout", descriptorSetLayout.vkDescriptorSetLayout
			);
			long computePipeline = instance.pipelines.createComputePipeline(
					stack, pipelineLayout, "shaders/fill.comp.spv", "FillBuffer"
			);

			var descriptorPool = descriptorSetLayout.createPool(1, 0, "FillPool");
			long descriptorSet = descriptorPool.allocate(stack, 1)[0];

			var descriptorWrites = VkWriteDescriptorSet.calloc(1, stack);
			instance.descriptors.writeBuffer(stack, descriptorWrites, descriptorSet, 0, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, buffer);

			vkUpdateDescriptorSets(instance.vkDevice(), descriptorWrites, null);

			var fence = instance.sync.fenceBank.borrowFence(false, "Filling");

			long commandPool = instance.commands.createPool(
					0, instance.queueFamilies().graphics().index(), "Filling"
			);
			var commandBuffer = instance.commands.createPrimaryBuffers(
					commandPool, 1, "Filling"
			)[0];

			instance.commands.begin(commandBuffer, stack, "Filling");

			vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, computePipeline);
			vkCmdBindDescriptorSets(
					commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, pipelineLayout,
					0, stack.longs(descriptorSet), null
			);
			vkCmdPushConstants(
					commandBuffer, pipelineLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0,
					stack.ints(valuesPerInvocation)
			);
			vkCmdDispatch(commandBuffer, groupCount, 1, 1);

			assertVkSuccess(vkEndCommandBuffer(commandBuffer), "EndCommandBuffer", "Filling");
			long startTime = System.currentTimeMillis();
			instance.queueFamilies().graphics().first().submit(
					commandBuffer, "Filling", new WaitSemaphore[0], fence
			);

			fence.awaitSignal();
			System.out.println("Submission took " + (System.currentTimeMillis() - startTime) + " ms");

			for (int index = 0; index < hostBuffer.limit(); index++) {
				assertEquals(123456, hostBuffer.get(index));
			}

			instance.sync.fenceBank.returnFence(fence);
			descriptorPool.destroy();
			vkDestroyCommandPool(instance.vkDevice(), commandPool, null);
			vkDestroyPipeline(instance.vkDevice(), computePipeline, null);
			descriptorSetLayout.destroy();
			vkDestroyPipelineLayout(instance.vkDevice(), pipelineLayout, null);
			vmaDestroyBuffer(instance.vmaAllocator(), buffer.vkBuffer(), buffer.vmaAllocation());
		}

		instance.destroyInitialObjects();
	}
}
