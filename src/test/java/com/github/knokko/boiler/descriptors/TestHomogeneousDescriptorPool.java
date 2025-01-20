package com.github.knokko.boiler.descriptors;

import com.github.knokko.boiler.builders.BoilerBuilder;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class TestHomogeneousDescriptorPool {

	@Test
	public void testAllocateManyDescriptors() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_0, "TestPool", 1
		).validation().forbidValidationErrors().build();

		VkbDescriptorSetLayout layout;
		try (var stack = stackPush()) {
			var bindings = VkDescriptorSetLayoutBinding.calloc(1, stack);
			instance.descriptors.binding(bindings, 0, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, VK_SHADER_STAGE_FRAGMENT_BIT);
			layout = instance.descriptors.createLayout(stack, bindings, "TestLayout");
		}

		int amount = 10_000;
		var pool = layout.createPool(amount, 0, "LargePool");

		var sets = pool.allocate(amount);
		Arrays.sort(sets);

		assertEquals(amount, sets.length);
		for (int index = 1; index < amount; index++) assertNotEquals(sets[index - 1], sets[index]);

		pool.destroy();
		layout.destroy();
		instance.destroyInitialObjects();
	}
}
