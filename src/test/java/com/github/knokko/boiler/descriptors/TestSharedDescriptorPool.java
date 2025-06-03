package com.github.knokko.boiler.descriptors;

import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.memory.MemoryCombiner;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class TestSharedDescriptorPool {

	@Test
	public void testEmptySharedDescriptorPool() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_0, "TestEmptySharedDescriptorPool", 1
		).validation().forbidValidationErrors().build();

		VkbDescriptorSetLayout layout;
		try (var stack = stackPush()) {
			var builder = new DescriptorSetLayoutBuilder(stack, 1);
			builder.set(0, 0, VK_DESCRIPTOR_TYPE_SAMPLER, VK_SHADER_STAGE_FRAGMENT_BIT);
			layout = builder.build(instance, "Empty");
		}

		var vkDescriptorPool = new DescriptorCombiner(instance).build("Empty");
		assertEquals(VK_NULL_HANDLE, vkDescriptorPool);

		vkDestroyDescriptorSetLayout(instance.vkDevice(), layout.vkDescriptorSetLayout, null);
		instance.destroyInitialObjects();
	}

	@Test
	public void testSharedDescriptorPool() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_0, "TestEmptySharedDescriptorPool", 1
		).validation().forbidValidationErrors().build();

		var combiner = new MemoryCombiner(instance, "Memory");
		var uniformBuffer = combiner.addBuffer(
				1234L, instance.deviceProperties.limits().minUniformBufferOffsetAlignment(),
				VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT
		);
		var storageBuffer = combiner.addBuffer(
				456L, instance.deviceProperties.limits().minStorageBufferOffsetAlignment(),
				VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
		);
		var memory = combiner.build(false);

		VkbDescriptorSetLayout uniformBufferLayout, combinedLayout;
		try (var stack = stackPush()) {
			var builder = new DescriptorSetLayoutBuilder(stack, 1);
			builder.set(0, 0, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_SHADER_STAGE_FRAGMENT_BIT);
			uniformBufferLayout = builder.build(instance, "UniformLayout");
		}
		try (var stack = stackPush()) {
			var builder = new DescriptorSetLayoutBuilder(stack, 4);
			builder.set(0, 0, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_SHADER_STAGE_GEOMETRY_BIT);
			builder.set(1, 1, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_SHADER_STAGE_VERTEX_BIT);
			builder.set(2, 2, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_SHADER_STAGE_FRAGMENT_BIT);
			builder.set(3, 3, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_SHADER_STAGE_GEOMETRY_BIT);
			combinedLayout = builder.build(instance, "CombinedLayout");
		}

		var descriptors = new DescriptorCombiner(instance);
		var combinedSets2 = new long[1];
		descriptors.addSingle(combinedLayout, descriptorSet -> combinedSets2[0] = descriptorSet);
		var uniformSets = descriptors.addMultiple(uniformBufferLayout, 2);
		var combinedSets1 = descriptors.addMultiple(combinedLayout, 5);
		long vkDescriptorPool = descriptors.build("SharedPool");

		try (var stack = stackPush()) {
			var updater = new DescriptorUpdater(stack, 26);
			updater.writeUniformBuffer(0, uniformSets[0], 0, uniformBuffer);
			updater.writeUniformBuffer(1, uniformSets[1], 0, uniformBuffer);
			for (int index = 0; index < 6; index++) {
				long combinedSet = index < 5 ? combinedSets1[index] : combinedSets2[index - 5];
				updater.writeStorageBuffer(2 + 4 * index, combinedSet, 0, storageBuffer);
				updater.writeStorageBuffer(3 + 4 * index, combinedSet, 2, storageBuffer);
				updater.writeStorageBuffer(4 + 4 * index, combinedSet, 3, storageBuffer);
				updater.writeUniformBuffer(5 + 4 * index, combinedSet, 1, uniformBuffer);
			}
			updater.update(instance);
		}

		vkDestroyDescriptorPool(instance.vkDevice(), vkDescriptorPool, null);
		vkDestroyDescriptorSetLayout(instance.vkDevice(), uniformBufferLayout.vkDescriptorSetLayout, null);
		vkDestroyDescriptorSetLayout(instance.vkDevice(), combinedLayout.vkDescriptorSetLayout, null);
		memory.destroy(instance);
		instance.destroyInitialObjects();
	}

	@Test
	public void testAllocateManyDescriptors() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_0, "TestPool", 1
		).validation().forbidValidationErrors().build();

		VkbDescriptorSetLayout layout;
		try (var stack = stackPush()) {
			var builder = new DescriptorSetLayoutBuilder(stack, 1);
			builder.set(0, 0, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, VK_SHADER_STAGE_FRAGMENT_BIT);
			layout = builder.build(instance, "TestMany");
		}

		int amount = 10_000;
		var descriptors = new DescriptorCombiner(instance);
		var bulk1 = descriptors.addMultiple(layout, amount);
		var bulk2 = descriptors.addMultiple(layout, amount);
		var solo = new long[2];
		descriptors.addSingle(layout, descriptorSet -> solo[0] = descriptorSet);
		descriptors.addSingle(layout, descriptorSet -> solo[1] = descriptorSet);
		long vkDescriptorPool = descriptors.build("LargePool");

		var sets = new HashSet<Long>();
		for (long set : bulk1) sets.add(set);
		for (long set : bulk2) sets.add(set);
		for (long set : solo) sets.add(set);

		assertEquals(2 * amount + 2, sets.size());

		vkDestroyDescriptorPool(instance.vkDevice(), vkDescriptorPool, null);
		vkDestroyDescriptorSetLayout(instance.vkDevice(), layout.vkDescriptorSetLayout, null);
		instance.destroyInitialObjects();
	}
}
