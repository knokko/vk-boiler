package com.github.knokko.boiler.queue;

import com.github.knokko.boiler.builder.queue.MinimalQueueFamilyMapper;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VkQueueFamilyProperties;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memPutInt;
import static org.lwjgl.vulkan.VK10.*;

public class TestMinimalQueueMapper {

    @Test
    public void testAllCombined() {
        try (var stack = stackPush()) {
            var pQueueFamilies = VkQueueFamilyProperties.calloc(3, stack);
            memPutInt(pQueueFamilies.get(0).address() + VkQueueFamilyProperties.QUEUEFLAGS, VK_QUEUE_GRAPHICS_BIT);
            memPutInt(
                    pQueueFamilies.get(1).address() + VkQueueFamilyProperties.QUEUEFLAGS,
                    VK_QUEUE_GRAPHICS_BIT | VK_QUEUE_COMPUTE_BIT
            );
            memPutInt(
                    pQueueFamilies.get(2).address() + VkQueueFamilyProperties.QUEUEFLAGS,
                    VK_QUEUE_TRANSFER_BIT | VK_QUEUE_COMPUTE_BIT
            );

            boolean[] presentSupport = { true, true, false };

            var mapping = new MinimalQueueFamilyMapper().mapQueueFamilies(pQueueFamilies, presentSupport);
            mapping.validate();
            assertEquals(1, mapping.graphicsFamilyIndex());
            assertEquals(1, mapping.computeFamilyIndex());
            assertEquals(1, mapping.transferFamilyIndex());
            assertEquals(1, mapping.presentFamilyIndex());
            assertArrayEquals(new float[] { 1f }, mapping.graphicsPriorities());
        }
    }

    @Test
    public void testAllDistinct() {
        try (var stack = stackPush()) {
            var pQueueFamilies = VkQueueFamilyProperties.calloc(3, stack);
            memPutInt(pQueueFamilies.get(0).address() + VkQueueFamilyProperties.QUEUEFLAGS, VK_QUEUE_GRAPHICS_BIT);
            memPutInt(
                    pQueueFamilies.get(1).address() + VkQueueFamilyProperties.QUEUEFLAGS,
                    VK_QUEUE_COMPUTE_BIT | VK_QUEUE_TRANSFER_BIT
            );
            memPutInt(pQueueFamilies.get(2).address() + VkQueueFamilyProperties.QUEUEFLAGS, VK_QUEUE_TRANSFER_BIT);

            boolean[] presentSupport = { false, false, true };

            var mapping = new MinimalQueueFamilyMapper().mapQueueFamilies(pQueueFamilies, presentSupport);
            mapping.validate();
            assertEquals(0, mapping.graphicsFamilyIndex());
            assertEquals(1, mapping.computeFamilyIndex());
            assertEquals(0, mapping.transferFamilyIndex());
            assertEquals(2, mapping.presentFamilyIndex());
            for (var priorities : new float[][] { mapping.graphicsPriorities(), mapping.computePriorities(), mapping.transferPriorities() }) {
                assertArrayEquals(new float[] { 1f }, priorities);
            }
        }
    }

    @Test
    public void testPartiallyCombined() {
        try (var stack = stackPush()) {
            var pQueueFamilies = VkQueueFamilyProperties.calloc(4, stack);
            memPutInt(pQueueFamilies.get(0).address() + VkQueueFamilyProperties.QUEUEFLAGS, VK_QUEUE_GRAPHICS_BIT);
            memPutInt(
                    pQueueFamilies.get(1).address() + VkQueueFamilyProperties.QUEUEFLAGS,
                    VK_QUEUE_COMPUTE_BIT | VK_QUEUE_TRANSFER_BIT
            );
            memPutInt(
                    pQueueFamilies.get(2).address() + VkQueueFamilyProperties.QUEUEFLAGS,
                    VK_QUEUE_COMPUTE_BIT | VK_QUEUE_GRAPHICS_BIT
            );
            memPutInt(pQueueFamilies.get(3).address() + VkQueueFamilyProperties.QUEUEFLAGS, VK_QUEUE_TRANSFER_BIT);

            boolean[] presentSupport = { true, false, false, true };

            var mapping = new MinimalQueueFamilyMapper().mapQueueFamilies(pQueueFamilies, presentSupport);
            mapping.validate();
            assertEquals(2, mapping.graphicsFamilyIndex());
            assertEquals(2, mapping.computeFamilyIndex());
            assertEquals(2, mapping.transferFamilyIndex());
            assertEquals(0, mapping.presentFamilyIndex());
            for (var priorities : new float[][] { mapping.graphicsPriorities(), mapping.computePriorities(), mapping.transferPriorities() }) {
                assertArrayEquals(new float[] { 1f }, priorities);
            }
        }
    }
}
