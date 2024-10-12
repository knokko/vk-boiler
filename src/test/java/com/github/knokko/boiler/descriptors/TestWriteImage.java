package com.github.knokko.boiler.descriptors;

import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.commands.CommandRecorder;
import com.github.knokko.boiler.synchronization.ResourceUsage;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.*;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
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

		var destBuffer = instance.buffers.createMapped(4, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, "DestBuffer");
		var sourceBuffer = instance.buffers.createMapped(4, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, "SourceBuffer");
		memPutInt(sourceBuffer.hostAddress(), 100);

		var image = instance.images.createSimple(
				1, 1, VK_FORMAT_R32_SINT,
				VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
				VK_IMAGE_ASPECT_COLOR_BIT, "Image"
		);

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
			imageInfo.imageView(image.vkImageView());
			imageInfo.imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

			var descriptorWrites = VkWriteDescriptorSet.calloc(2, stack);
			instance.descriptors.writeBuffer(
					stack, descriptorWrites, descriptorSet,
					0, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, destBuffer.fullRange()
			);
			instance.descriptors.writeImage(descriptorWrites, descriptorSet, 1, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, imageInfo);

			vkUpdateDescriptorSets(instance.vkDevice(), descriptorWrites, null);

			var fence = instance.sync.fenceBank.borrowFence(false, "Fence");

			long commandPool = instance.commands.createPool(
					0, instance.queueFamilies().compute().index(), "CommandPool"
			);
			var commandBuffer = instance.commands.createPrimaryBuffers(
					commandPool, 1, "Sampling"
			)[0];
			var recorder = CommandRecorder.begin(commandBuffer, instance, stack, "Sampling");

			recorder.transitionLayout(image, null, ResourceUsage.TRANSFER_DEST);
			recorder.copyBufferToImage(image, sourceBuffer.vkBuffer());
			recorder.transitionLayout(
					image, ResourceUsage.TRANSFER_DEST, ResourceUsage.shaderRead(VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT)
			);

			vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, computePipeline);
			recorder.bindComputeDescriptors(pipelineLayout, descriptorSet);
			vkCmdDispatch(commandBuffer, 1, 1, 1);

			recorder.end();
			instance.queueFamilies().graphics().first().submit(
					commandBuffer, "Sampling", null, fence
			);

			fence.waitAndReset();

			assertEquals(100, memGetInt(destBuffer.hostAddress()));

			instance.sync.fenceBank.returnFence(fence);
			descriptorPool.destroy();
			vkDestroyCommandPool(instance.vkDevice(), commandPool, null);
			vkDestroyPipeline(instance.vkDevice(), computePipeline, null);
			descriptorSetLayout.destroy();
			vkDestroyPipelineLayout(instance.vkDevice(), pipelineLayout, null);
			vkDestroySampler(instance.vkDevice(), sampler, null);
			destBuffer.destroy(instance);
			sourceBuffer.destroy(instance);
			image.destroy(instance);
		}

		instance.destroyInitialObjects();
	}
}
