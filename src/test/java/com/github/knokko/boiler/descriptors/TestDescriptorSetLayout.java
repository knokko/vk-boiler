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

	@Test
	public void testDescriptorTypeCounts() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_3, "TestDescriptorSetLayout", 1
		).validation().forbidValidationErrors().build();

		try (var stack = stackPush()) {
			var bindings = VkDescriptorSetLayoutBinding.calloc(3, stack);
			var uniform1 = bindings.get(0);
			uniform1.binding(0);
			uniform1.descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
			uniform1.descriptorCount(1);
			uniform1.stageFlags(VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT);
			uniform1.pImmutableSamplers(null);
			var sampler = bindings.get(1);
			sampler.binding(1);
			sampler.descriptorType(VK_DESCRIPTOR_TYPE_SAMPLER);
			sampler.descriptorCount(5);
			sampler.stageFlags(VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);
			sampler.pImmutableSamplers(null);
			var uniform2 = bindings.get(2);
			uniform2.binding(2);
			uniform2.descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
			uniform2.descriptorCount(2);
			uniform2.stageFlags(VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);
			uniform2.pImmutableSamplers(null);

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
