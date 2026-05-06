package com.github.knokko.boiler.descriptors;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.commands.SingleTimeCommands;
import com.github.knokko.boiler.images.ImageBuilder;
import com.github.knokko.boiler.images.VkbImage;
import com.github.knokko.boiler.memory.MemoryCombiner;
import com.github.knokko.boiler.memory.callbacks.CallbackUserData;
import com.github.knokko.boiler.memory.callbacks.SumAllocationCallbacks;
import com.github.knokko.boiler.synchronization.ResourceUsage;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryStack;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.VK10.*;

public class TestStorageImages {

	@Test
	public void testWithDescriptorUpdater() {
		runTest(data -> {
			var updater = new DescriptorUpdater(data.stack, 3);
			updater.write(0, data.descriptorSet, 0, VK_DESCRIPTOR_TYPE_STORAGE_TEXEL_BUFFER);
			//noinspection resource
			updater.descriptorWrites.get(0).pTexelBufferView(data.stack.longs(data.sourceBufferView1));

			updater.write(1, data.descriptorSet, 1, VK_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER);
			//noinspection resource
			updater.descriptorWrites.get(1).pTexelBufferView(data.stack.longs(data.sourceBufferView2));

			updater.writeImage(
					2, data.descriptorSet, 2, data.image.vkImageView, VK_NULL_HANDLE,
					VK_DESCRIPTOR_TYPE_STORAGE_IMAGE, VK_IMAGE_LAYOUT_GENERAL
			);
			updater.update(data.instance);
		});
	}

	@Test
	public void testWithBulkDescriptorUpdater() {
		runTest(data -> {
			var updater = new BulkDescriptorUpdater(data.instance, data.stack, 10, 2, 1);
			updater.write(data.descriptorSet, 0, VK_DESCRIPTOR_TYPE_STORAGE_TEXEL_BUFFER);
			//noinspection resource
			updater.descriptorWrites.get(updater.descriptorWrites.position() - 1).pTexelBufferView(
					data.stack.longs(data.sourceBufferView1)
			);

			updater.write(data.descriptorSet, 1, VK_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER);
			//noinspection resource
			updater.descriptorWrites.get(updater.descriptorWrites.position() - 1).pTexelBufferView(
					data.stack.longs(data.sourceBufferView2)
			);

			updater.writeImage(
					data.descriptorSet, 2, data.image.vkImageView, VK_NULL_HANDLE,
					VK_DESCRIPTOR_TYPE_STORAGE_IMAGE, VK_IMAGE_LAYOUT_GENERAL
			);
			updater.finish();
		});
	}

	private void runTest(Consumer<DescriptorData> updateDescriptorSet) {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_0, "TestWriteStorageImage", 1
		)
				.allocationCallbacks(new SumAllocationCallbacks())
				.validation().forbidValidationErrors().build();

		var combiner = new MemoryCombiner(instance, "Memory");
		var sourceBuffer1 = combiner.addMappedBuffer(
				4, instance.deviceProperties.limits().minTexelBufferOffsetAlignment(),
				VK_BUFFER_USAGE_STORAGE_TEXEL_BUFFER_BIT
		);
		var sourceBuffer2 = combiner.addMappedBuffer(
				4, instance.deviceProperties.limits().minTexelBufferOffsetAlignment(),
				VK_BUFFER_USAGE_UNIFORM_TEXEL_BUFFER_BIT
		);
		var destinationBuffer = combiner.addMappedBuffer(4, 4, VK_BUFFER_USAGE_TRANSFER_DST_BIT);
		var image = combiner.addImage(new ImageBuilder(
				"StorageImage", 4, 1
		).setUsage(VK_IMAGE_USAGE_STORAGE_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT).format(VK_FORMAT_R8_UINT), 0.5f);

		var memory = combiner.build(true);

		memPutByte(sourceBuffer1.hostAddress, (byte) 12);
		memPutByte(sourceBuffer1.hostAddress + 1L, (byte) 18);
		memPutByte(sourceBuffer1.hostAddress + 2L, (byte) 100);
		memPutByte(sourceBuffer1.hostAddress + 3L, (byte) 200);

		memPutByte(sourceBuffer2.hostAddress, (byte) -2);
		memPutByte(sourceBuffer2.hostAddress + 1L, (byte) 2);
		memPutByte(sourceBuffer2.hostAddress + 2L, (byte) 28);
		memPutByte(sourceBuffer2.hostAddress + 3L, (byte) -100);

		try (var stack = stackPush()) {
			long sourceBufferView1 = sourceBuffer1.createView(VK_FORMAT_R8_UINT, instance, "SourceView1");
			long sourceBufferView2 = sourceBuffer2.createView(VK_FORMAT_R8_SINT, instance, "SourceView2");

			var layoutBuilder = new DescriptorSetLayoutBuilder(stack, 3);
			layoutBuilder.set(0, 0, VK_DESCRIPTOR_TYPE_STORAGE_TEXEL_BUFFER, VK_SHADER_STAGE_COMPUTE_BIT);
			layoutBuilder.set(1, 1, VK_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER, VK_SHADER_STAGE_COMPUTE_BIT);
			layoutBuilder.set(2, 2, VK_DESCRIPTOR_TYPE_STORAGE_IMAGE, VK_SHADER_STAGE_COMPUTE_BIT);
			var descriptorSetLayout = layoutBuilder.build(instance, "DSLayout");

			var descriptors = new DescriptorCombiner(instance);
			long[] descriptorSet = descriptors.addMultiple(descriptorSetLayout, 1);
			long descriptorPool = descriptors.build("StorageImageDescriptors");

			long pipelineLayout = instance.pipelines.createLayout(
					null, "PipelineLayout", descriptorSetLayout.vkDescriptorSetLayout
			);
			long computePipeline = instance.pipelines.createComputePipeline(
					pipelineLayout, "shaders/texels.comp.spv", "TexelPipeline"
			);

			updateDescriptorSet.accept(new DescriptorData(
					stack, descriptorSet[0], instance, sourceBufferView1, sourceBufferView2, image
			));

			SingleTimeCommands.submit(instance, "Texels", recorder -> {
				recorder.transitionLayout(image, null, ResourceUsage.compute(
						VK_IMAGE_LAYOUT_GENERAL, VK_ACCESS_SHADER_WRITE_BIT
				));

				vkCmdBindPipeline(recorder.commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, computePipeline);
				recorder.bindComputeDescriptors(pipelineLayout, descriptorSet);
				vkCmdDispatch(recorder.commandBuffer, 4, 1, 1);

				recorder.transitionLayout(image, ResourceUsage.compute(
						VK_IMAGE_LAYOUT_GENERAL, VK_ACCESS_SHADER_WRITE_BIT
				), ResourceUsage.TRANSFER_SOURCE);
				recorder.copyImageToBuffer(image, destinationBuffer);
				recorder.bufferBarrier(destinationBuffer, ResourceUsage.TRANSFER_DEST, ResourceUsage.HOST_READ);
			}).destroy();

			assertEquals(100, memGetByte(destinationBuffer.hostAddress));
			assertEquals(10, memGetByte(destinationBuffer.hostAddress + 1L));
			assertEquals(20, memGetByte(destinationBuffer.hostAddress + 2L));
			assertEquals(-128, memGetByte(destinationBuffer.hostAddress + 3L));

			assertInstanceOf(SumAllocationCallbacks.class, instance.allocationCallbacks);
			vkDestroyDescriptorPool(instance.vkDevice(), descriptorPool, CallbackUserData.DESCRIPTOR_POOL.put(stack, instance));
			vkDestroyPipeline(instance.vkDevice(), computePipeline, CallbackUserData.PIPELINE.put(stack, instance));
			vkDestroyDescriptorSetLayout(
					instance.vkDevice(), descriptorSetLayout.vkDescriptorSetLayout,
					CallbackUserData.DESCRIPTOR_SET_LAYOUT.put(stack, instance)
			);
			vkDestroyPipelineLayout(instance.vkDevice(), pipelineLayout, CallbackUserData.PIPELINE_LAYOUT.put(stack, instance));
			vkDestroyBufferView(instance.vkDevice(), sourceBufferView1, CallbackUserData.BUFFER_VIEW.put(stack, instance));
			vkDestroyBufferView(instance.vkDevice(), sourceBufferView2, CallbackUserData.BUFFER_VIEW.put(stack, instance));
			memory.destroy(instance);
		}

		instance.destroyInitialObjects();
	}

	private record DescriptorData(
			MemoryStack stack, long descriptorSet, BoilerInstance instance,
			long sourceBufferView1, long sourceBufferView2, VkbImage image
	) {}
}
