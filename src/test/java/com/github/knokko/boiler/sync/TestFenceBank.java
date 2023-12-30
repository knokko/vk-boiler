package com.github.knokko.boiler.sync;

import com.github.knokko.boiler.builder.BoilerBuilder;
import com.github.knokko.boiler.commands.CommandRecorder;
import com.github.knokko.boiler.instance.BoilerInstance;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.junit.jupiter.api.Assertions.*;
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
                    instance.vkDevice(), stack.longs(fence), true, instance.defaultTimeout
            ), "WaitForFences", "Signal");
        }

        vkDestroyCommandPool(instance.vkDevice(), commandPool, null);
    }

    @Test
    public void complexFenceBankTest() {
        var instance = new BoilerBuilder(VK_API_VERSION_1_0, "TestFenceBank", 1)
                .validation()
                .forbidValidationErrors()
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

    static boolean contains(long[] array, long target) {
        return Arrays.stream(array).anyMatch(candidate -> candidate == target);
    }

    @Test
    public void testBulkActions() {
        var instance = new BoilerBuilder(VK_API_VERSION_1_0, "TestFenceBankBulk", 1)
                .validation()
                .forbidValidationErrors()
                .build();

        var bank = instance.sync.fenceBank;

        long[] fences = bank.borrowFences(10);

        assertEquals(VK_NOT_READY, vkGetFenceStatus(instance.vkDevice(), fences[5]));
        signalFence(instance, fences[3]);
        assertEquals(VK_SUCCESS, vkGetFenceStatus(instance.vkDevice(), fences[3]));

        bank.returnFences(true, fences[3], fences[5]);
        assertEquals(VK_NOT_READY, vkGetFenceStatus(instance.vkDevice(), fences[3]));

        long[] newFences = bank.borrowFences(3);
        assertTrue(contains(newFences, fences[3]));
        assertTrue(contains(newFences, fences[5]));
        assertFalse(contains(newFences, fences[4]));

        bank.returnFences(false, fences[0], fences[1], fences[2], fences[4]);
        bank.returnFences(false, newFences);
        bank.returnFences(false, Arrays.copyOfRange(fences, 6, 10));

        instance.destroyInitialObjects();
    }

    @Test
    public void testBorrowSignaled() {
        var instance = new BoilerBuilder(VK_API_VERSION_1_0, "TestFenceBankBulk", 1)
                .validation()
                .forbidValidationErrors()
                .build();
        var bank = instance.sync.fenceBank;

        FatFence[] fences = bank.borrowSignaledFences(5);
        long[] rawFences = Arrays.stream(fences).mapToLong(fatFence -> fatFence.vkFence).toArray();
        assertEquals(5, fences.length);
        for (var fence : fences) {
            assertTrue(fence.hostSignaled);
            assertTrue(fence.isSignaled(instance));
        }

        bank.returnFences(true, fences[0], fences[1], fences[2]);

        FatFence[] newFences = bank.borrowSignaledFences(5);
        long[] newRawFences = Arrays.stream(newFences).mapToLong(fatFence -> fatFence.vkFence).toArray();
        assertTrue(contains(newRawFences, rawFences[0]));
        assertFalse(contains(newRawFences, rawFences[3]));
        assertTrue(contains(rawFences, newRawFences[0]));
        assertFalse(contains(rawFences, newRawFences[4]));

        for (var fence : newFences) {
            assertTrue(fence.hostSignaled);
            assertTrue(fence.isSignaled(instance));
        }
        bank.returnFences(false, newFences);
        bank.returnFences(false, fences[3], fences[4]);

        instance.destroyInitialObjects();
    }
}
