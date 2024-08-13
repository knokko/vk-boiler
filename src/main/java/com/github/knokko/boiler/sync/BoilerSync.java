package com.github.knokko.boiler.sync;

import com.github.knokko.boiler.instance.BoilerInstance;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRTimelineSemaphore.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.*;

public class BoilerSync {

    private final BoilerInstance instance;
    private final boolean usesTimelineSemaphoreExtension;
    public final FenceBank fenceBank;
    public final SemaphoreBank semaphoreBank;

    public BoilerSync(BoilerInstance instance) {
        this.instance = instance;
        this.usesTimelineSemaphoreExtension = instance.deviceExtensions.contains(VK_KHR_TIMELINE_SEMAPHORE_EXTENSION_NAME);
        this.fenceBank = new FenceBank(instance);
        this.semaphoreBank = new SemaphoreBank(instance);
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

    public long createTimelineSemaphore(long initialValue, String name) {
        try (var stack = stackPush()) {
            var ciTimeline = VkSemaphoreTypeCreateInfo.calloc(stack);
            ciTimeline.sType$Default();
            ciTimeline.semaphoreType(VK_SEMAPHORE_TYPE_TIMELINE);
            ciTimeline.initialValue(initialValue);

            var ciSemaphore = VkSemaphoreCreateInfo.calloc(stack);
            ciSemaphore.sType$Default();
            ciSemaphore.pNext(ciTimeline);
            ciSemaphore.flags(0);

            var pSemaphore = stack.callocLong(1);
            assertVkSuccess(vkCreateSemaphore(
                    instance.vkDevice(), ciSemaphore, null, pSemaphore
            ), "CreateSemaphore", name);
            long semaphore = pSemaphore.get(0);
            instance.debug.name(stack, semaphore, VK_OBJECT_TYPE_SEMAPHORE, name);
            return semaphore;
        }
    }

    public void awaitTimelineSemaphore(MemoryStack stack, long semaphore, long value, String context) {
        var wiSemaphore = VkSemaphoreWaitInfo.calloc(stack);
        wiSemaphore.sType$Default();
        wiSemaphore.flags(0);
        wiSemaphore.semaphoreCount(1);
        wiSemaphore.pSemaphores(stack.longs(semaphore));
        wiSemaphore.pValues(stack.longs(value));

        if (usesTimelineSemaphoreExtension) {
            assertVkSuccess(vkWaitSemaphoresKHR(
                    instance.vkDevice(), wiSemaphore, instance.defaultTimeout
            ), "WaitSemaphoresKHR", context);
        } else {
            assertVkSuccess(vkWaitSemaphores(
                    instance.vkDevice(), wiSemaphore, instance.defaultTimeout
            ), "WaitSemaphores", context);
        }
    }

    public long getTimelineSemaphoreValue(MemoryStack stack, long semaphore, String context) {
        var pValue = stack.callocLong(1);
        if (usesTimelineSemaphoreExtension) {
            assertVkSuccess(vkGetSemaphoreCounterValueKHR(
                    instance.vkDevice(), semaphore, pValue
            ), "GetSemaphoreCounterValueKHR", context);
        } else {
            assertVkSuccess(vkGetSemaphoreCounterValue(
                    instance.vkDevice(), semaphore, pValue
            ), "GetSemaphoreCounterValue", context);
        }
        return pValue.get(0);
    }

    public void setTimelineSemaphoreValue(MemoryStack stack, long semaphore, long newValue, String context) {
        var siSemaphore = VkSemaphoreSignalInfo.calloc(stack);
        siSemaphore.sType$Default();
        siSemaphore.semaphore(semaphore);
        siSemaphore.value(newValue);

        if (usesTimelineSemaphoreExtension) {
            assertVkSuccess(vkSignalSemaphoreKHR(
                    instance.vkDevice(), siSemaphore
            ), "SignalSemaphore", context);
        } else {
            assertVkSuccess(vkSignalSemaphore(
                    instance.vkDevice(), siSemaphore
            ), "SignalSemaphore", context);
        }
    }
}
