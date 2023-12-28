package com.github.knokko.boiler.sync;

import com.github.knokko.boiler.instance.BoilerInstance;
import org.lwjgl.system.MemoryStack;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.vulkan.VK10.*;

/**
 * Represents a wrapper for a <i>VkFence</i> that can not only be signaled by the device, but also by the host. This is
 * sometimes convenient.<br>
 *
 * To signal from the host, simply set <i>hostSignaled</i> to true. All methods of this class (<i>reset</i>,
 * <i>wait</i>, etc...) will look at <i>hostSignaled</i>.
 */
public class FatFence {

    public final long vkFence;
    public boolean hostSignaled;

    public FatFence(long vkFence, boolean startHostSignaled) {
        this.vkFence = vkFence;
        this.hostSignaled = startHostSignaled;
    }

    public void reset(BoilerInstance instance, MemoryStack stack) {
        assertVkSuccess(vkResetFences(
                instance.vkDevice(), stack.longs(vkFence)
        ), "ResetFences", "FatFence");
        this.hostSignaled = false;
    }

    public void wait(BoilerInstance instance, MemoryStack stack) {
        wait(instance, stack, instance.defaultTimeout);
    }

    public void wait(BoilerInstance instance, MemoryStack stack, long timeout) {
        if (!hostSignaled) {
            assertVkSuccess(vkWaitForFences(
                    instance.vkDevice(), stack.longs(vkFence), true, timeout
            ), "WaitForFences", "FatFence");
        }
    }

    public void waitAndReset(BoilerInstance instance, MemoryStack stack) {
        waitAndReset(instance, stack, instance.defaultTimeout);
    }

    public void waitAndReset(BoilerInstance instance, MemoryStack stack, long timeout) {
        this.wait(instance, stack, timeout);
        this.reset(instance, stack);
    }

    public boolean isSignaled(BoilerInstance instance) {
        return hostSignaled || vkGetFenceStatus(instance.vkDevice(), vkFence) == VK_SUCCESS;
    }
}
