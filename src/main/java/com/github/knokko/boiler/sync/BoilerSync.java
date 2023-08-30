package com.github.knokko.boiler.sync;

import com.github.knokko.boiler.instance.BoilerInstance;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class BoilerSync {

    private final BoilerInstance instance;

    public BoilerSync(BoilerInstance instance) {
        this.instance = instance;
    }

    public long[] createFences(boolean startSignaled, int amount, String name) {
        long[] fences = new long[amount];
        try (var stack = stackPush()) {
            var ciFence = VkFenceCreateInfo.calloc(stack);
            ciFence.sType$Default();
            ciFence.flags(startSignaled ? VK_FENCE_CREATE_SIGNALED_BIT : 0);

            var pFence = stack.callocLong(1);

            for (int index = 0; index < amount; index++) {
                assertVkSuccess(vkCreateFence(
                        instance.vkDevice(), ciFence, null, pFence
                ), "CreateFence", name + index);
                instance.debug.name(stack, pFence.get(0), VK_OBJECT_TYPE_FENCE, name + index);
                fences[index] = pFence.get(0);
            }
            return fences;
        }
    }

    public long[] createSemaphores(String name, int amount) {
        long[] semaphores = new long[amount];
        try (var stack = stackPush()) {
            var ciSemaphore = VkSemaphoreCreateInfo.calloc(stack);
            ciSemaphore.sType$Default();
            ciSemaphore.flags(0);

            var pSemaphore = stack.callocLong(1);
            for (int index = 0; index < amount; index++) {
                assertVkSuccess(vkCreateSemaphore(
                        instance.vkDevice(), ciSemaphore, null, pSemaphore
                ), "CreateSemaphore", name);
                long semaphore = pSemaphore.get(0);
                instance.debug.name(stack, semaphore, VK_OBJECT_TYPE_SEMAPHORE, name);
                semaphores[index] = semaphore;
            }
            return semaphores;
        }
    }

    public void waitAndReset(MemoryStack stack, long fence, long timeout) {
        assertVkSuccess(vkWaitForFences(
                instance.vkDevice(), stack.longs(fence), true, timeout
        ), "WaitForFences", "SwapchainAcquire");
        assertVkSuccess(vkResetFences(
                instance.vkDevice(), stack.longs(fence)
        ), "ResetFences", "SwapchainAcquire");
    }
}
