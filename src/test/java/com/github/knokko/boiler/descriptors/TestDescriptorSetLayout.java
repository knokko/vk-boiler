package com.github.knokko.boiler.descriptors;

import com.github.knokko.boiler.builders.BoilerBuilder;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK13.VK_API_VERSION_1_3;

public class TestDescriptorSetLayout {

	@SuppressWarnings("resource")
	@Test
	public void testDescriptorTypeCounts() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_3, "TestDescriptorSetLayout", 1
		).validation().forbidValidationErrors().build();

		try (var stack = stackPush()) {
			var bindings = VkDescriptorSetLayoutBinding.calloc(3, stack);
			instance.descriptors.binding(bindings, 0, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_SHADER_STAGE_COMPUTE_BIT);
			instance.descriptors.binding(bindings, 1, VK_DESCRIPTOR_TYPE_SAMPLER, VK_SHADER_STAGE_FRAGMENT_BIT);
			bindings.get(1).descriptorCount(5);
			instance.descriptors.binding(bindings, 2, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_SHADER_STAGE_FRAGMENT_BIT);
			bindings.get(2).descriptorCount(2);

			var layout = instance.descriptors.createLayout(stack, bindings, "DSLayout");

			var expected = new HashMap<Integer, Integer>();
			expected.put(VK_DESCRIPTOR_TYPE_SAMPLER, 5);
			expected.put(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 3);

			assertEquals(expected, layout.descriptorTypeCounts);

			layout.destroy();
		}

		instance.destroyInitialObjects();
	}
}
