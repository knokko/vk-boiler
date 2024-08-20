package com.github.knokko.boiler.queue;

import com.github.knokko.boiler.builder.queue.MinimalQueueFamilyMapper;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VkQueueFamilyProperties;

import java.util.HashSet;
import java.util.Set;

import static com.github.knokko.boiler.util.CollectionHelper.createSet;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memPutInt;
import static org.lwjgl.vulkan.KHRVideoDecodeQueue.VK_KHR_VIDEO_DECODE_QUEUE_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRVideoDecodeQueue.VK_QUEUE_VIDEO_DECODE_BIT_KHR;
import static org.lwjgl.vulkan.KHRVideoEncodeQueue.VK_KHR_VIDEO_ENCODE_QUEUE_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRVideoEncodeQueue.VK_QUEUE_VIDEO_ENCODE_BIT_KHR;
import static org.lwjgl.vulkan.VK10.*;

public class TestMinimalQueueMapper {

    private static final Set<String> VIDEO_EXTENSIONS = createSet(
            VK_KHR_VIDEO_DECODE_QUEUE_EXTENSION_NAME,
            VK_KHR_VIDEO_ENCODE_QUEUE_EXTENSION_NAME
    );

    @Test
    public void testAllCombined() {
        try (var stack = stackPush()) {
            var pQueueFamilies = VkQueueFamilyProperties.calloc(3, stack);
            memPutInt(pQueueFamilies.get(0).address() + VkQueueFamilyProperties.QUEUEFLAGS, VK_QUEUE_GRAPHICS_BIT);
            memPutInt(
                    pQueueFamilies.get(1).address() + VkQueueFamilyProperties.QUEUEFLAGS,
                    VK_QUEUE_GRAPHICS_BIT | VK_QUEUE_COMPUTE_BIT | VK_QUEUE_VIDEO_DECODE_BIT_KHR
            );
            memPutInt(
                    pQueueFamilies.get(2).address() + VkQueueFamilyProperties.QUEUEFLAGS,
                    VK_QUEUE_TRANSFER_BIT | VK_QUEUE_COMPUTE_BIT
            );

            boolean[][] presentSupportMatrix = { { true }, { true }, { false } };

            var mapping = new MinimalQueueFamilyMapper().mapQueueFamilies(
                    pQueueFamilies, VIDEO_EXTENSIONS, presentSupportMatrix
            );
            mapping.validate();
            assertEquals(1, mapping.graphics().index());
            assertEquals(1, mapping.compute().index());
            assertEquals(1, mapping.transfer().index());
            assertArrayEquals(new int[] { 1 }, mapping.presentFamilyIndices());
            assertNull(mapping.videoEncode());
            assertEquals(1, mapping.videoDecode().index());
            assertArrayEquals(new float[] { 1f }, mapping.graphics().priorities());
        }
    }

    @Test
    public void testAllDistinct() {
        try (var stack = stackPush()) {
            var pQueueFamilies = VkQueueFamilyProperties.calloc(4, stack);
            memPutInt(pQueueFamilies.get(0).address() + VkQueueFamilyProperties.QUEUEFLAGS, VK_QUEUE_GRAPHICS_BIT);
            memPutInt(
                    pQueueFamilies.get(1).address() + VkQueueFamilyProperties.QUEUEFLAGS,
                    VK_QUEUE_COMPUTE_BIT | VK_QUEUE_TRANSFER_BIT
            );
            memPutInt(pQueueFamilies.get(2).address() + VkQueueFamilyProperties.QUEUEFLAGS, VK_QUEUE_TRANSFER_BIT);
            memPutInt(pQueueFamilies.get(3).address() + VkQueueFamilyProperties.QUEUEFLAGS, VK_QUEUE_VIDEO_ENCODE_BIT_KHR);

            boolean[][] presentSupportMatrix = { { false }, { false }, { true }, { true } };

            var mapping = new MinimalQueueFamilyMapper().mapQueueFamilies(
                    pQueueFamilies, VIDEO_EXTENSIONS, presentSupportMatrix
            );
            mapping.validate();
            assertEquals(0, mapping.graphics().index());
            assertEquals(1, mapping.compute().index());
            assertEquals(0, mapping.transfer().index());
            assertArrayEquals(new int[] { 2 }, mapping.presentFamilyIndices());
            assertEquals(3, mapping.videoEncode().index());
            assertNull(mapping.videoDecode());
            for (var priorities : new float[][] {
                    mapping.graphics().priorities(),
                    mapping.compute().priorities(),
                    mapping.transfer().priorities(),
                    mapping.videoEncode().priorities()
            }) {
                assertArrayEquals(new float[] { 1f }, priorities);
            }
        }
    }

    @Test
    public void testPartiallyCombined() {
        try (var stack = stackPush()) {
            var pQueueFamilies = VkQueueFamilyProperties.calloc(5, stack);
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
            memPutInt(
                    pQueueFamilies.get(4).address() + VkQueueFamilyProperties.QUEUEFLAGS,
                    VK_QUEUE_VIDEO_ENCODE_BIT_KHR | VK_QUEUE_VIDEO_DECODE_BIT_KHR
            );

            boolean[][] presentSupportMatrix = { { true }, { false }, { false }, { true }, { false } };

            var mapping = new MinimalQueueFamilyMapper().mapQueueFamilies(
                    pQueueFamilies, VIDEO_EXTENSIONS, presentSupportMatrix
            );
            mapping.validate();
            assertEquals(2, mapping.graphics().index());
            assertEquals(2, mapping.compute().index());
            assertEquals(2, mapping.transfer().index());
            assertArrayEquals(new int[1], mapping.presentFamilyIndices());
            assertEquals(4, mapping.videoEncode().index());
            assertEquals(4, mapping.videoDecode().index());
            for (var priorities : new float[][] {
                    mapping.graphics().priorities(),
                    mapping.compute().priorities(),
                    mapping.transfer().priorities(),
                    mapping.videoEncode().priorities(),
                    mapping.videoDecode().priorities()
            }) {
                assertArrayEquals(new float[] { 1f }, priorities);
            }
        }
    }

    @Test
    public void testIgnoreVideoQueueFamiliesWhenExtensionsAreNotEnabled() {
        try (var stack = stackPush()) {
            var pQueueFamilies = VkQueueFamilyProperties.calloc(2, stack);
            memPutInt(
                    pQueueFamilies.get(0).address() + VkQueueFamilyProperties.QUEUEFLAGS,
                    VK_QUEUE_VIDEO_ENCODE_BIT_KHR | VK_QUEUE_VIDEO_DECODE_BIT_KHR
            );
            memPutInt(
                    pQueueFamilies.get(1).address() + VkQueueFamilyProperties.QUEUEFLAGS,
                    VK_QUEUE_GRAPHICS_BIT | VK_QUEUE_COMPUTE_BIT
            );

            boolean[][] presentSupportMatrix = { { true }, { true } };

            var mapping = new MinimalQueueFamilyMapper().mapQueueFamilies(
                    pQueueFamilies, new HashSet<>(), presentSupportMatrix
            );
            mapping.validate();
            assertEquals(1, mapping.graphics().index());
            assertEquals(1, mapping.compute().index());
            assertEquals(1, mapping.transfer().index());
            assertNull(mapping.videoDecode());
            assertNull(mapping.videoEncode());
        }
    }

    @Test
    public void testWithoutAnyVideoFamilies() {
        try (var stack = stackPush()) {
            var pQueueFamilies = VkQueueFamilyProperties.calloc(1, stack);
            memPutInt(
                    pQueueFamilies.get(0).address() + VkQueueFamilyProperties.QUEUEFLAGS,
                    VK_QUEUE_GRAPHICS_BIT | VK_QUEUE_COMPUTE_BIT
            );

            boolean[][] presentSupportMatrix = { { true } };

            var mapping = new MinimalQueueFamilyMapper().mapQueueFamilies(
                    pQueueFamilies, VIDEO_EXTENSIONS, presentSupportMatrix
            );
            mapping.validate();
            assertEquals(0, mapping.graphics().index());
            assertEquals(0, mapping.compute().index());
            assertEquals(0, mapping.transfer().index());
            assertNull(mapping.videoDecode());
            assertNull(mapping.videoEncode());
        }
    }

    @Test
    public void testWithMultipleWindows() {
        try (var stack = stackPush()) {
            var pQueueFamilies = VkQueueFamilyProperties.calloc(3, stack);
            memPutInt(
                    pQueueFamilies.get(0).address() + VkQueueFamilyProperties.QUEUEFLAGS,
                    VK_QUEUE_VIDEO_ENCODE_BIT_KHR | VK_QUEUE_COMPUTE_BIT | VK_QUEUE_TRANSFER_BIT
            );
            memPutInt(
                    pQueueFamilies.get(1).address() + VkQueueFamilyProperties.QUEUEFLAGS,
                    VK_QUEUE_TRANSFER_BIT
            );
            memPutInt(
                    pQueueFamilies.get(2).address() + VkQueueFamilyProperties.QUEUEFLAGS,
                    VK_QUEUE_GRAPHICS_BIT | VK_QUEUE_COMPUTE_BIT
            );

            boolean[][] presentSupportMatrix = {
                    { true, false, true },
                    { true, true, true },
                    { true, false, true }
            };
            var mapping = new MinimalQueueFamilyMapper().mapQueueFamilies(pQueueFamilies, new HashSet<>(), presentSupportMatrix);
            assertEquals(2, mapping.graphics().index());
            assertEquals(2, mapping.compute().index());
            assertEquals(2, mapping.transfer().index());
            assertNull(mapping.videoEncode());
            assertNull(mapping.videoDecode());
            assertEquals(2, mapping.presentFamilyIndices()[0]);
            assertEquals(1, mapping.presentFamilyIndices()[1]);
            assertEquals(2, mapping.presentFamilyIndices()[2]);
        }
    }
}
