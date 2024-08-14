package com.github.knokko.boiler.buffer;

import com.github.knokko.boiler.builder.BoilerBuilder;
import com.github.knokko.boiler.commands.CommandRecorder;
import com.github.knokko.boiler.sync.ResourceUsage;
import com.github.knokko.boiler.sync.WaitSemaphore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memByteBuffer;
import static org.lwjgl.vulkan.VK10.*;

public class TestBufferCopies {

    @Test
    public void testBufferCopies() {
        var instance = new BoilerBuilder(
                VK_API_VERSION_1_0, "Test buffer copies", VK_MAKE_VERSION(1, 0, 0)
        ).validation().forbidValidationErrors().build();

        var sourceBuffer = instance.buffers.createMapped(
                100, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, "source"
        );
        var sourceHostBuffer = memByteBuffer(sourceBuffer.hostAddress(), 100);
        var middleBuffer = instance.buffers.create(
                100, VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_TRANSFER_SRC_BIT, "middle"
        );
        var destinationBuffer = instance.buffers.createMapped(
                100, VK_BUFFER_USAGE_TRANSFER_DST_BIT, "destination"
        );
        var destinationHostBuffer = memByteBuffer(destinationBuffer.hostAddress(), 100);

        for (int index = 0; index < 100; index++) {
            sourceHostBuffer.put((byte) index);
        }

        try (var stack = stackPush()) {
            var fence = instance.sync.fenceBank.borrowFence(false, "Copying");
            var commandPool = instance.commands.createPool(
                    0, instance.queueFamilies().graphics().index(), "Copy"
            );
            var commandBuffer = instance.commands.createPrimaryBuffers(
                    commandPool, 1, "Copy"
            )[0];

            var recorder = CommandRecorder.begin(commandBuffer, instance, stack, "Copying");

            recorder.copyBuffer(100, sourceBuffer.vkBuffer(), 0, middleBuffer.vkBuffer(), 0);
            recorder.bufferBarrier(
                    middleBuffer.vkBuffer(), 0, 100, ResourceUsage.TRANSFER_DEST, ResourceUsage.TRANSFER_SOURCE
            );
            recorder.copyBuffer(100, middleBuffer.vkBuffer(), 0, destinationBuffer.vkBuffer(), 0);

            recorder.end();

            instance.queueFamilies().graphics().queues().get(0).submit(
                    commandBuffer, "Copying", new WaitSemaphore[0], fence
            );
            fence.awaitSignal();
            instance.sync.fenceBank.returnFence(fence);
            vkDestroyCommandPool(instance.vkDevice(), commandPool, null);
        }

        for (int index = 0; index < 100; index++) {
            assertEquals((byte) index, destinationHostBuffer.get());
        }

        sourceBuffer.destroy(instance.vmaAllocator());
        middleBuffer.destroy(instance.vmaAllocator());
        destinationBuffer.destroy(instance.vmaAllocator());
        instance.destroyInitialObjects();
    }
}
