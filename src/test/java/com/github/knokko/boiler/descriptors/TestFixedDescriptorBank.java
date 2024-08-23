package com.github.knokko.boiler.descriptors;

import com.github.knokko.boiler.builders.BoilerBuilder;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class TestFixedDescriptorBank {

	@Test
	public void testFixedDescriptorBank() {
		var instance = new BoilerBuilder(VK_API_VERSION_1_0, "TestFixedDescriptorBank", 1)
				.validation()
				.forbidValidationErrors()
				.build();

		VkbDescriptorSetLayout descriptorSetLayout;
		try (var stack = stackPush()) {
			var bindings = VkDescriptorSetLayoutBinding.calloc(1, stack);
			instance.descriptors.binding(bindings, 0, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_SHADER_STAGE_VERTEX_BIT);
			bindings.descriptorCount(5);

			descriptorSetLayout = instance.descriptors.createLayout(stack, bindings, "Test");
		}

		var bank = new FixedDescriptorBank(descriptorSetLayout, 2, 0, "Test");

		long descriptorSet1 = bank.borrowDescriptorSet("DS1");
		long descriptorSet2 = bank.borrowDescriptorSet("DS2");
		assertNotEquals(0, descriptorSet1);
		assertNotEquals(0, descriptorSet2);
		assertNotEquals(descriptorSet1, descriptorSet2);
		assertNull(bank.borrowDescriptorSet("ShouldBeNull"));

		bank.returnDescriptorSet(descriptorSet1);
		assertEquals(descriptorSet1, bank.borrowDescriptorSet("DS1"));

		bank.returnDescriptorSet(descriptorSet2);
		assertEquals(descriptorSet2, bank.borrowDescriptorSet("DS2"));

		assertNull(bank.borrowDescriptorSet("ShouldBeNull"));

		bank.returnDescriptorSet(descriptorSet1);
		bank.returnDescriptorSet(descriptorSet2);

		long descriptorSet12 = bank.borrowDescriptorSet("DS12");
		long descriptorSet21 = bank.borrowDescriptorSet("DS21");
		assertNotEquals(0, descriptorSet12);
		assertNotEquals(0, descriptorSet21);
		assertNull(bank.borrowDescriptorSet("ShouldBeNull"));
		assertNotEquals(descriptorSet12, descriptorSet21);
		assertTrue(descriptorSet12 == descriptorSet1 || descriptorSet12 == descriptorSet2);
		assertTrue(descriptorSet21 == descriptorSet1 || descriptorSet21 == descriptorSet2);

		bank.destroy(false);
		descriptorSetLayout.destroy();
		instance.destroyInitialObjects();
	}
}
