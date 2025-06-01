package com.github.knokko.boiler.descriptors;

import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.commands.SingleTimeCommands;
import com.github.knokko.boiler.images.ImageBuilder;
import com.github.knokko.boiler.memory.MemoryBlockBuilder;
import com.github.knokko.boiler.synchronization.ResourceUsage;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memGetInt;
import static org.lwjgl.system.MemoryUtil.memPutInt;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.VK_API_VERSION_1_2;

public class TestWriteImage {

	@Test
	public void testWriteImage() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_2, "TestWriteImage", 1
		).validation().forbidValidationErrors().build();

		var memoryBuilder = new MemoryBlockBuilder(instance, "Memory");
		var destinationBuffer = memoryBuilder.addMappedBuffer(
				4, instance.deviceProperties.limits().minStorageBufferOffsetAlignment(),
				VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
		);
		var sourceBuffer = memoryBuilder.addMappedBuffer(4, 1, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);

		var image = memoryBuilder.addImage(new ImageBuilder("Image", 1, 1).texture().format(VK_FORMAT_R32_SINT));
		var memory = memoryBuilder.allocate(true);

		memPutInt(sourceBuffer.hostAddress, 100);
		try (var stack = stackPush()) {
			var layoutBindings = VkDescriptorSetLayoutBinding.calloc(2, stack);
			instance.descriptors.binding(layoutBindings, 0, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_SHADER_STAGE_COMPUTE_BIT);
			instance.descriptors.binding(layoutBindings, 1, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, VK_SHADER_STAGE_COMPUTE_BIT);

			var descriptorSetLayout = instance.descriptors.createLayout(stack, layoutBindings, "DSLayout");
			long pipelineLayout = instance.pipelines.createLayout(
					null, "PipelineLayout", descriptorSetLayout.vkDescriptorSetLayout
			);
			long computePipeline = instance.pipelines.createComputePipeline(
					pipelineLayout, "shaders/sample.comp.spv", "SamplePipeline"
			);

			var descriptorPool = descriptorSetLayout.createPool(1, 0, "DescriptorPool");
			long descriptorSet = descriptorPool.allocate(1)[0];

			var sampler = instance.images.createSampler(
					VK_FILTER_NEAREST, VK_SAMPLER_MIPMAP_MODE_NEAREST, VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER,
					0f, 0f, false, "Sampler"
			);

			var imageInfo = VkDescriptorImageInfo.calloc(1, stack);
			imageInfo.sampler(sampler);
			imageInfo.imageView(image.vkImageView);
			imageInfo.imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

			var descriptorWrites = VkWriteDescriptorSet.calloc(2, stack);
			instance.descriptors.writeBuffer(
					stack, descriptorWrites, descriptorSet,
					0, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, destinationBuffer
			);
			instance.descriptors.writeImage(descriptorWrites, descriptorSet, 1, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, imageInfo);

			vkUpdateDescriptorSets(instance.vkDevice(), descriptorWrites, null);

			var commands = new SingleTimeCommands(instance);
			commands.submit("Sampling", recorder -> {
				recorder.transitionLayout(image, null, ResourceUsage.TRANSFER_DEST);
				recorder.copyBufferToImage(image, sourceBuffer);
				recorder.transitionLayout(
						image, ResourceUsage.TRANSFER_DEST, ResourceUsage.shaderRead(VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT)
				);

				vkCmdBindPipeline(recorder.commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, computePipeline);
				recorder.bindComputeDescriptors(pipelineLayout, descriptorSet);
				vkCmdDispatch(recorder.commandBuffer, 1, 1, 1);
			}).awaitCompletion();
			commands.destroy();

			assertEquals(100, memGetInt(destinationBuffer.hostAddress));

			descriptorPool.destroy();
			vkDestroyPipeline(instance.vkDevice(), computePipeline, null);
			descriptorSetLayout.destroy();
			vkDestroyPipelineLayout(instance.vkDevice(), pipelineLayout, null);
			vkDestroySampler(instance.vkDevice(), sampler, null);
			memory.free(instance);
		}

		instance.destroyInitialObjects();
	}
}
