package com.github.knokko.boiler.descriptors;

import com.github.knokko.boiler.builders.BoilerBuilder;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class TestGrowingDescriptorBank {

	@Test
	public void testGrowingDescriptorBank() {
		var instance = new BoilerBuilder(VK_API_VERSION_1_0, "TestFixedDescriptorBank", 1)
				.validation()
				.forbidValidationErrors()
				.build();

		VkbDescriptorSetLayout layout;
		try (var stack = stackPush()) {
			var bindings = VkDescriptorSetLayoutBinding.calloc(1, stack);
			instance.descriptors.binding(bindings, 0, VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE, VK_SHADER_STAGE_VERTEX_BIT);
			bindings.descriptorCount(3);

			layout = instance.descriptors.createLayout(stack, bindings, "Test");
		}

		var bank = new GrowingDescriptorBank(layout, 0);

		var initialSets = new long[15];
		for (int index = 0; index < initialSets.length; index++) {
			initialSets[index] = bank.borrowDescriptorSet("InitialSet");
		}
		assertUnique(initialSets);

		for (int index = 5; index < 12; index++) {
			bank.returnDescriptorSet(initialSets[index]);
		}

		var finalSets = new HashSet<Long>();
		for (int index = 0; index < 200; index++) {
			finalSets.add(bank.borrowDescriptorSet("FinalSet"));
		}
		assertEquals(200, finalSets.size());

		for (int index = 0; index < initialSets.length; index++) {
			if (index >= 5 && index < 12) {
				assertTrue(finalSets.contains(initialSets[index]));
			} else {
				assertFalse(finalSets.contains(initialSets[index]));
			}
		}

		bank.destroy(false);
		layout.destroy();
		instance.destroyInitialObjects();
	}

	private void assertUnique(long[] array) {
		Set<Long> set = new HashSet<>(array.length);
		for (long element : array) set.add(element);
		assertEquals(set.size(), array.length);
	}
}
