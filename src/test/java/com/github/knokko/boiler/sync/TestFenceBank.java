package com.github.knokko.boiler.sync;

import com.github.knokko.boiler.builder.BoilerBuilder;
import com.github.knokko.boiler.builder.instance.ValidationFeatures;
import com.github.knokko.boiler.commands.CommandRecorder;
import com.github.knokko.boiler.instance.BoilerInstance;
import org.junit.jupiter.api.Test;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class TestFenceBank {

    private void signalFence(BoilerInstance instance, long fence) {
        var commandPool = instance.commands.createPool(
                0, instance.queueFamilies().graphics().index(), "SignalFence"
        );
        var commandBuffer = instance.commands.createPrimaryBuffers(commandPool, 1, "Signal")[0];
        try (var stack = stackPush()) {
            var commands = CommandRecorder.begin(commandBuffer, instance, stack, "Signal");
            commands.end();

            instance.queueFamilies().graphics().queues().get(0).submit(
                    commandBuffer, "Signal", new WaitSemaphore[0], fence
            );
            assertVkSuccess(vkWaitForFences(
                    instance.vkDevice(), stack.longs(fence), true, 100_000_000L
            ), "WaitForFences", "Signal");
        }

        vkDestroyCommandPool(instance.vkDevice(), commandPool, null);
    }

    @Test
    public void complexFenceBankTest() {
        var instance = new BoilerBuilder(VK_API_VERSION_1_0, "TestFenceBank", 1)
                .validation(new ValidationFeatures(false, false, false, true, true))
                .build();

        var fence1 = instance.sync.fenceBank.borrowFence();
        assertEquals(VK_NOT_READY, vkGetFenceStatus(instance.vkDevice(), fence1));
        signalFence(instance, fence1);
        assertEquals(VK_SUCCESS, vkGetFenceStatus(instance.vkDevice(), fence1));

        var fence2 = instance.sync.fenceBank.borrowFence();
        assertEquals(VK_NOT_READY, vkGetFenceStatus(instance.vkDevice(), fence2));

        instance.sync.fenceBank.returnFence(fence1, true);
        assertEquals(fence1, instance.sync.fenceBank.borrowFence());
        assertEquals(VK_NOT_READY, vkGetFenceStatus(instance.vkDevice(), fence1));

        instance.sync.fenceBank.returnFence(fence2, false);
        assertEquals(fence2, instance.sync.fenceBank.borrowFence());
        assertEquals(VK_NOT_READY, vkGetFenceStatus(instance.vkDevice(), fence2));

        instance.sync.fenceBank.returnFence(fence1, true);
        instance.sync.fenceBank.returnFence(fence2, false);
        instance.destroyInitialObjects();
    }
}
