package com.github.knokko.boiler.descriptors;

import com.github.knokko.boiler.builders.BoilerBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK13.VK_API_VERSION_1_3;

public class TestDescriptorSetLayoutBuilder {

	@Test
	public void testDescriptorTypeCounts() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_3, "TestDescriptorSetLayout", 1
		).validation().forbidValidationErrors().build();

		try (var stack = stackPush()) {
			var builder = new DescriptorSetLayoutBuilder(stack, 3);
			builder.set(0, 0, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_SHADER_STAGE_COMPUTE_BIT);
			builder.set(1, 1, VK_DESCRIPTOR_TYPE_SAMPLER, VK_SHADER_STAGE_FRAGMENT_BIT);
			builder.set(2, 2, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_SHADER_STAGE_FRAGMENT_BIT);
			builder.bindings.get(1).descriptorCount(5);
			builder.bindings.get(2).descriptorCount(2);
			var layout = builder.build(instance, "DSLayout");

			assertNotEquals(VK_NULL_HANDLE, layout.vkDescriptorSetLayout);
			assertEquals(5, layout.descriptorTypes.get(VK_DESCRIPTOR_TYPE_SAMPLER));
			assertEquals(3, layout.descriptorTypes.get(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER));
			vkDestroyDescriptorSetLayout(instance.vkDevice(), layout.vkDescriptorSetLayout, null);
		}

		instance.destroyInitialObjects();
	}
}
