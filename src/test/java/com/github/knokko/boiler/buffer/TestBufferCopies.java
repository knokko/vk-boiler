package com.github.knokko.boiler.buffer;

import com.github.knokko.boiler.builder.BoilerBuilder;
import com.github.knokko.boiler.commands.CommandRecorder;
import com.github.knokko.boiler.sync.ResourceUsage;
import com.github.knokko.boiler.sync.WaitSemaphore;
import org.junit.jupiter.api.Test;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memByteBuffer;
import static org.lwjgl.vulkan.VK10.*;

public class TestBufferCopies {

    @Test
    public void testBufferCopies() {
        var boiler = new BoilerBuilder(
                VK_API_VERSION_1_0, "Test buffer copies", VK_MAKE_VERSION(1, 0, 0)
        ).validation().forbidValidationErrors().build();

        var sourceBuffer = boiler.buffers.createMapped(
                100, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, "source"
        );
        var sourceHostBuffer = memByteBuffer(sourceBuffer.hostAddress(), 100);
        var middleBuffer = boiler.buffers.create(
                100, VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_TRANSFER_SRC_BIT, "middle"
        );
        var destinationBuffer = boiler.buffers.createMapped(
                100, VK_BUFFER_USAGE_TRANSFER_DST_BIT, "destination"
        );
        var destinationHostBuffer = memByteBuffer(destinationBuffer.hostAddress(), 100);

        for (int index = 0; index < 100; index++) {
            sourceHostBuffer.put((byte) index);
        }

        try (var stack = stackPush()) {
            var fence = boiler.sync.createFences(false, 1, "Copying")[0];
            var commandPool = boiler.commands.createPool(
                    0, boiler.queueFamilies().graphics().index(), "Copy"
            );
            var commandBuffer = boiler.commands.createPrimaryBuffers(
                    commandPool, 1, "Copy"
            )[0];

            var recorder = CommandRecorder.begin(commandBuffer, boiler, stack, "Copying");

            recorder.copyBuffer(100, sourceBuffer.vkBuffer(), 0, middleBuffer.vkBuffer(), 0);
            recorder.bufferBarrier(
                    middleBuffer.vkBuffer(), 0, 100, ResourceUsage.TRANSFER_DEST, ResourceUsage.TRANSFER_SOURCE
            );
            recorder.copyBuffer(100, middleBuffer.vkBuffer(), 0, destinationBuffer.vkBuffer(), 0);

            recorder.end();

            boiler.queueFamilies().graphics().queues().get(0).submit(
                    commandBuffer, "Copying", new WaitSemaphore[0], fence
            );
            assertVkSuccess(vkWaitForFences(
                    boiler.vkDevice(), stack.longs(fence), true, boiler.defaultTimeout
            ), "WaitForFences", "Copying");

            vkDestroyFence(boiler.vkDevice(), fence, null);
            vkDestroyCommandPool(boiler.vkDevice(), commandPool, null);
        }

        for (int index = 0; index < 100; index++) {
            assertEquals((byte) index, destinationHostBuffer.get());
        }

        sourceBuffer.destroy(boiler.vmaAllocator());
        middleBuffer.destroy(boiler.vmaAllocator());
        destinationBuffer.destroy(boiler.vmaAllocator());
        boiler.destroyInitialObjects();
    }
}
