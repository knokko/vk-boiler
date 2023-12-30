package com.github.knokko.boiler.descriptors;

import com.github.knokko.boiler.builder.BoilerBuilder;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class TestFixedDescriptorBank {

    @Test
    public void testFixedDescriptorBank() {
        var boiler = new BoilerBuilder(VK_API_VERSION_1_0, "TestFixedDescriptorBank", 1)
                .validation()
                .forbidValidationErrors()
                .build();

        long descriptorSetLayout;
        try (var stack = stackPush()) {
            var bindings = VkDescriptorSetLayoutBinding.calloc(1, stack);
            bindings.binding(0);
            bindings.descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
            bindings.descriptorCount(5);
            bindings.stageFlags(VK_SHADER_STAGE_VERTEX_BIT);

            descriptorSetLayout = boiler.descriptors.createLayout(stack, bindings, "Test");
        }
        var bank = new FixedDescriptorBank(boiler, descriptorSetLayout, "Test", (stack, ciPool) -> {
            var poolSizes = VkDescriptorPoolSize.calloc(1, stack);
            poolSizes.type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
            poolSizes.descriptorCount(5);

            ciPool.flags(0);
            ciPool.maxSets(2);
            ciPool.pPoolSizes(poolSizes);
        });

        long descriptorSet1 = bank.borrowDescriptorSet();
        long descriptorSet2 = bank.borrowDescriptorSet();
        assertNotEquals(descriptorSet1, descriptorSet2);
        assertNull(bank.borrowDescriptorSet());

        bank.returnDescriptorSet(descriptorSet1);
        assertEquals(descriptorSet1, bank.borrowDescriptorSet());

        bank.returnDescriptorSet(descriptorSet2);
        assertEquals(descriptorSet2, bank.borrowDescriptorSet());

        assertNull(bank.borrowDescriptorSet());

        bank.returnDescriptorSet(descriptorSet1);
        bank.returnDescriptorSet(descriptorSet2);

        long descriptorSet12 = bank.borrowDescriptorSet();
        long descriptorSet21 = bank.borrowDescriptorSet();
        assertNull(bank.borrowDescriptorSet());
        assertNotEquals(descriptorSet12, descriptorSet21);
        assertTrue(descriptorSet12 == descriptorSet1 || descriptorSet12 == descriptorSet2);
        assertTrue(descriptorSet21 == descriptorSet1 || descriptorSet21 == descriptorSet2);

        bank.destroy(false);

        vkDestroyDescriptorSetLayout(boiler.vkDevice(), descriptorSetLayout, null);
        boiler.destroyInitialObjects();
    }
}
