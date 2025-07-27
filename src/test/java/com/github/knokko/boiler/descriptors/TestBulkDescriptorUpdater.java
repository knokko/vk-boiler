package com.github.knokko.boiler.descriptors;

import com.github.knokko.boiler.buffers.VkbBuffer;
import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.memory.MemoryBlock;
import com.github.knokko.boiler.memory.MemoryCombiner;
import org.junit.jupiter.api.Test;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.VK_API_VERSION_1_1;
import static org.lwjgl.vulkan.VK13.VK_API_VERSION_1_3;

public class TestBulkDescriptorUpdater {

	@Test
	public void testZeroCapacity() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_1, "TestZeroCapacityDescriptors", 1
		).validation().forbidValidationErrors().build();
		new BulkDescriptorUpdater(instance, null, 0, 0, 0).finish();
		try (var stack = stackPush()) {
			new BulkDescriptorUpdater(instance, stack, 0, 0, 0).finish();
		}
		instance.destroyInitialObjects();
	}

	@Test
	public void testZeroUpdates() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_1, "TestZeroUpdateDescriptors", 1
		).validation().forbidValidationErrors().build();
		new BulkDescriptorUpdater(instance, null, 10, 5, 5).finish();
		try (var stack = stackPush()) {
			new BulkDescriptorUpdater(instance, stack, 10, 5, 5).finish();
		}
		instance.destroyInitialObjects();
	}

	@Test
	public void testCapacityTooLarge() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_3, "TestLargeCapacityDescriptors", 1
		).validation().forbidValidationErrors().build();

		MemoryBlock memory;
		VkbBuffer uniformBuffer;
		{
			var combiner = new MemoryCombiner(instance, "Uniforms");
			uniformBuffer = combiner.addBuffer(100L, 4L, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, 0f);
			memory = combiner.build(false);
		}

		VkbDescriptorSetLayout layout;
		try (var stack = stackPush()) {
			var builder = new DescriptorSetLayoutBuilder(stack, 1);
			builder.set(0, 0, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_SHADER_STAGE_FRAGMENT_BIT);
			layout = builder.build(instance, "UniformFragment");
		}

		var combiner = new DescriptorCombiner(instance);
		long[] descriptorSets = combiner.addMultiple(layout, 2);
		long vkDescriptorPool = combiner.build("UniformFragments");

		var updater = new BulkDescriptorUpdater(instance, null, 10, 5, 0);
		updater.writeUniformBuffer(descriptorSets[0], 0, uniformBuffer);
		updater.finish();

		try (var stack = stackPush()) {
			updater = new BulkDescriptorUpdater(instance, stack, 10, 5, 0);
			updater.writeUniformBuffer(descriptorSets[1], 0, uniformBuffer);
			updater.finish();
		}

		vkDestroyDescriptorPool(instance.vkDevice(), vkDescriptorPool, null);
		vkDestroyDescriptorSetLayout(instance.vkDevice(), layout.vkDescriptorSetLayout, null);
		memory.destroy(instance);
		instance.destroyInitialObjects();
	}
}
