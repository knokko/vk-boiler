package com.github.knokko.boiler.compute;

import com.github.knokko.boiler.builder.BoilerBuilder;
import com.github.knokko.boiler.builder.instance.ValidationFeatures;
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
        var boiler = new BoilerBuilder(
                VK_API_VERSION_1_2, "TestSimpleComputeShader", VK_MAKE_VERSION(0, 1, 0)
        )
                .validation(new ValidationFeatures(true, true, false, true, true))
                .forbidValidationErrors()
                .build();

        try (var stack = stackPush()) {
            int valuesPerInvocation = 16;
            int invocationsPerGroup = 128;
            int groupCount = 1024 * 2;

            var buffer = boiler.buffers.createMapped(
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

            long descriptorSetLayout = boiler.descriptors.createLayout(
                    stack, fillLayoutBindings, "FillBuffer-DescriptorSetLayout"
            );

            var pushConstants = VkPushConstantRange.calloc(1, stack);
            var sizePushConstant = pushConstants.get(0);
            sizePushConstant.stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
            sizePushConstant.offset(0);
            sizePushConstant.size(8);

            long pipelineLayout = boiler.pipelines.createLayout(
                    stack, pushConstants, "FillBuffer-PipelineLayout", descriptorSetLayout
            );
            long computePipeline = boiler.pipelines.createComputePipeline(
                    stack, pipelineLayout, "com/github/knokko/boiler/compute/fill.comp.spv", "FillBuffer"
            );

            var descriptorPoolSizes = VkDescriptorPoolSize.calloc(1, stack);
            descriptorPoolSizes.type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER);
            descriptorPoolSizes.descriptorCount(1);

            var ciDescriptorPool = VkDescriptorPoolCreateInfo.calloc(stack);
            ciDescriptorPool.sType$Default();
            ciDescriptorPool.flags(0);
            ciDescriptorPool.maxSets(1);
            ciDescriptorPool.pPoolSizes(descriptorPoolSizes);

            var pDescriptorPool = stack.callocLong(1);
            assertVkSuccess(vkCreateDescriptorPool(
                    boiler.vkDevice(), ciDescriptorPool, null, pDescriptorPool
            ), "CreateDescriptorPool", "Filling");
            long descriptorPool = pDescriptorPool.get(0);
            long descriptorSet = boiler.descriptors.allocate(stack, 1, descriptorPool, "Filling", descriptorSetLayout)[0];

            var descriptorWrites = VkWriteDescriptorSet.calloc(1, stack);
            descriptorWrites.sType$Default();
            descriptorWrites.dstSet(descriptorSet);
            descriptorWrites.dstBinding(0);
            descriptorWrites.dstArrayElement(0);
            descriptorWrites.descriptorCount(1);
            descriptorWrites.descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER);
            descriptorWrites.pBufferInfo(boiler.descriptors.bufferInfo(stack, buffer.asBuffer()));

            vkUpdateDescriptorSets(boiler.vkDevice(), descriptorWrites, null);

            long fence = boiler.sync.createFences(false, 1, "Filling")[0];

            long commandPool = boiler.commands.createPool(
                    0, boiler.queueFamilies().graphics().index(), "Filling"
            );
            var commandBuffer = boiler.commands.createPrimaryBuffers(
                    commandPool, 1, "Filling"
            )[0];
            boiler.commands.begin(commandBuffer, stack, "Filling");

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
            boiler.queueFamilies().graphics().queues().get(0).submit(
                    commandBuffer, "Filling", new WaitSemaphore[0], fence
            );

            assertVkSuccess(vkWaitForFences(
                    boiler.vkDevice(), stack.longs(fence), true, boiler.defaultTimeout
            ), "WaitForFences", "Filling");
            System.out.println("Submission took " + (System.currentTimeMillis() - startTime) + " ms");

            for (int index = 0; index < hostBuffer.limit(); index++) {
                assertEquals(123456, hostBuffer.get(index));
            }

            vkDestroyFence(boiler.vkDevice(), fence, null);
            vkDestroyDescriptorPool(boiler.vkDevice(), descriptorPool, null);
            vkDestroyCommandPool(boiler.vkDevice(), commandPool, null);
            vkDestroyPipeline(boiler.vkDevice(), computePipeline, null);
            vkDestroyDescriptorSetLayout(boiler.vkDevice(), descriptorSetLayout, null);
            vkDestroyPipelineLayout(boiler.vkDevice(), pipelineLayout, null);
            vmaDestroyBuffer(boiler.vmaAllocator(), buffer.vkBuffer(), buffer.vmaAllocation());
        }

        boiler.destroyInitialObjects();
    }
}
