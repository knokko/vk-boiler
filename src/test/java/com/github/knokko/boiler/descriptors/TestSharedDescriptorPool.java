package com.github.knokko.boiler.descriptors;

import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.memory.MemoryBlockBuilder;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import static org.junit.jupiter.api.Assertions.assertThrows;
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
			var bindings = VkDescriptorSetLayoutBinding.calloc(1, stack);
			instance.descriptors.binding(bindings, 0, VK_DESCRIPTOR_TYPE_SAMPLER, VK_SHADER_STAGE_FRAGMENT_BIT);
			layout = instance.descriptors.createLayout(stack, bindings, "NonEmpty");
		}

		var pool = new SharedDescriptorPoolBuilder(instance).build("Empty");
		assertThrows(IllegalArgumentException.class, () -> pool.allocate(layout, 1));

		pool.destroy(instance);
		layout.destroy();
		instance.destroyInitialObjects();
	}

	@Test
	public void testSharedDescriptorPool() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_0, "TestEmptySharedDescriptorPool", 1
		).validation().forbidValidationErrors().build();

		var memoryBuilder = new MemoryBlockBuilder(instance, "Memory");
		var uniformBuffer = memoryBuilder.addBuffer(
				1234L, instance.deviceProperties.limits().minUniformBufferOffsetAlignment(),
				VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT
		);
		var storageBuffer = memoryBuilder.addBuffer(
				456L, instance.deviceProperties.limits().minStorageBufferOffsetAlignment(),
				VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
		);
		var memory = memoryBuilder.allocate(false);

		VkbDescriptorSetLayout uniformBufferLayout, combinedLayout;
		try (var stack = stackPush()) {
			var bindings = VkDescriptorSetLayoutBinding.calloc(1, stack);
			instance.descriptors.binding(bindings, 0, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_SHADER_STAGE_FRAGMENT_BIT);
			uniformBufferLayout = instance.descriptors.createLayout(stack, bindings, "UniformLayout");
		}
		try (var stack = stackPush()) {
			var bindings = VkDescriptorSetLayoutBinding.calloc(4, stack);
			instance.descriptors.binding(bindings, 0, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_SHADER_STAGE_GEOMETRY_BIT);
			instance.descriptors.binding(bindings, 1, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_SHADER_STAGE_VERTEX_BIT);
			instance.descriptors.binding(bindings, 2, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_SHADER_STAGE_FRAGMENT_BIT);
			instance.descriptors.binding(bindings, 3, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_SHADER_STAGE_GEOMETRY_BIT);
			combinedLayout = instance.descriptors.createLayout(stack, bindings, "CombinedLayout");
		}

		var builder = new SharedDescriptorPoolBuilder(instance);
		builder.request(combinedLayout, 1);
		builder.request(uniformBufferLayout, 2);
		builder.request(combinedLayout, 5);

		var pool = builder.build("SharedPool");

		var uniformSets = pool.allocate(uniformBufferLayout, 2);
		assertThrows(IllegalArgumentException.class, () -> pool.allocate(uniformBufferLayout, 1));

		var combinedSets1 = pool.allocate(combinedLayout, 4);
		var combinedSets2 = pool.allocate(combinedLayout, 2);
		assertThrows(IllegalArgumentException.class, () -> pool.allocate(combinedLayout, 1));

		try (var stack = stackPush()) {
			var descriptorWrites = VkWriteDescriptorSet.calloc(1, stack);
			for (int index = 0; index < 2; index++) {
				instance.descriptors.writeBuffer(stack, descriptorWrites, uniformSets[index], 0, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, uniformBuffer);
				vkUpdateDescriptorSets(instance.vkDevice(), descriptorWrites, null);
			}

			descriptorWrites = VkWriteDescriptorSet.calloc(4, stack);
			for (int index = 0; index < 6; index++) {
				long combinedSet = index < 4 ? combinedSets1[index] : combinedSets2[index - 4];
				for (int binding : new int[] { 0, 2, 3 }) {
					instance.descriptors.writeBuffer(stack, descriptorWrites, combinedSet, binding, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, storageBuffer);
				}
				instance.descriptors.writeBuffer(stack, descriptorWrites, combinedSet, 1, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, uniformBuffer);
			}
		}

		pool.destroy(instance);
		uniformBufferLayout.destroy();
		combinedLayout.destroy();
		memory.free(instance);
		instance.destroyInitialObjects();
	}
}
