package com.github.knokko.boiler.descriptors;

import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.commands.SingleTimeCommands;
import com.github.knokko.boiler.images.ImageBuilder;
import com.github.knokko.boiler.memory.MemoryCombiner;
import com.github.knokko.boiler.synchronization.ResourceUsage;
import org.junit.jupiter.api.Test;

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

		var combiner = new MemoryCombiner(instance, "Memory");
		var destinationBuffer = combiner.addMappedBuffer(
				4, instance.deviceProperties.limits().minStorageBufferOffsetAlignment(),
				VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
		);
		var sourceBuffer = combiner.addMappedBuffer(4, 1, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);

		var image = combiner.addImage(new ImageBuilder("Image", 1, 1).texture().format(VK_FORMAT_R32_SINT));
		var memory = combiner.build(true);

		memPutInt(sourceBuffer.hostAddress, 100);
		try (var stack = stackPush()) {
			var layoutBuilder = new DescriptorSetLayoutBuilder(stack, 2);
			layoutBuilder.set(0, 0, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_SHADER_STAGE_COMPUTE_BIT);
			layoutBuilder.set(1, 1, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, VK_SHADER_STAGE_COMPUTE_BIT);
			var descriptorSetLayout = layoutBuilder.build(instance, "DSLayout");

			var descriptors = new DescriptorCombiner(instance);
			long[] descriptorSet = descriptors.addMultiple(descriptorSetLayout, 1);
			long descriptorPool = descriptors.build("ImageDescriptors");

			long pipelineLayout = instance.pipelines.createLayout(
					null, "PipelineLayout", descriptorSetLayout.vkDescriptorSetLayout
			);
			long computePipeline = instance.pipelines.createComputePipeline(
					pipelineLayout, "shaders/sample.comp.spv", "SamplePipeline"
			);

			var sampler = instance.images.createSampler(
					VK_FILTER_NEAREST, VK_SAMPLER_MIPMAP_MODE_NEAREST, VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER,
					0f, 0f, false, "Sampler"
			);

			var updater = new DescriptorUpdater(stack, 2);
			updater.writeStorageBuffer(0, descriptorSet[0], 0, destinationBuffer);
			updater.writeImage(1, descriptorSet[0], 1, image.vkImageView, sampler);
			updater.update(instance);

			SingleTimeCommands.submit(instance, "Sampling", recorder -> {
				recorder.transitionLayout(image, null, ResourceUsage.TRANSFER_DEST);
				recorder.copyBufferToImage(image, sourceBuffer);
				recorder.transitionLayout(
						image, ResourceUsage.TRANSFER_DEST, ResourceUsage.shaderRead(VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT)
				);

				vkCmdBindPipeline(recorder.commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, computePipeline);
				recorder.bindComputeDescriptors(pipelineLayout, descriptorSet);
				vkCmdDispatch(recorder.commandBuffer, 1, 1, 1);
			}).destroy();

			assertEquals(100, memGetInt(destinationBuffer.hostAddress));

			vkDestroyDescriptorPool(instance.vkDevice(), descriptorPool, null);
			vkDestroyPipeline(instance.vkDevice(), computePipeline, null);
			vkDestroyDescriptorSetLayout(instance.vkDevice(), descriptorSetLayout.vkDescriptorSetLayout, null);
			vkDestroyPipelineLayout(instance.vkDevice(), pipelineLayout, null);
			vkDestroySampler(instance.vkDevice(), sampler, null);
			memory.destroy(instance);
		}

		instance.destroyInitialObjects();
	}
}
