package com.github.knokko.boiler.sync;

import com.github.knokko.boiler.instance.BoilerInstance;
import org.lwjgl.system.MemoryStack;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.vulkan.VK10.*;

public class VkbFence implements Comparable<VkbFence> {

    private final BoilerInstance instance;
    private final long vkFence;
    private long submissionTime;
    private boolean signaled;

    public VkbFence(BoilerInstance instance, long vkFence, boolean startSignaled) {
        this.instance = instance;
        this.vkFence = vkFence;
        this.signaled = startSignaled;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof VkbFence && this.vkFence == ((VkbFence) other).vkFence;
    }

    @Override
    public int hashCode() {
        return (int) vkFence;
    }

    public void setName(String name, MemoryStack stack) {
        instance.debug.name(stack, vkFence, VK_OBJECT_TYPE_FENCE, name);
    }

    public synchronized boolean isPending() {
        if (submissionTime == 0) return false;

        var status = vkGetFenceStatus(instance.vkDevice(), vkFence);
        if (status == VK_NOT_READY) return true;
        assertVkSuccess(status, "GetFenceStatus", "BoilerFence.isPending");

        submissionTime = 0;
        signaled = true;
        return false;
    }

    /**
     * Marks this fence as pending, and returns the VkFence handle.
     * You should use this when you submit the fence manually.
     */
    public synchronized long getVkFenceAndSubmit() {
        if (submissionTime != 0) throw new IllegalStateException("This fence is already pending");
        if (signaled) throw new IllegalStateException("This fence is still signaled");
        submissionTime = System.nanoTime();
        return vkFence;
    }

    public synchronized void reset(MemoryStack stack) {
        if (isPending()) throw new IllegalStateException("Fence is still pending");

        signaled = false;
        assertVkSuccess(vkResetFences(
                instance.vkDevice(), stack.longs(vkFence)
        ), "ResetFences", "BoilerFence.reset");
    }

    public synchronized void signal() {
        if (isPending()) throw new IllegalStateException("Fence is still pending");
        signaled = true;
    }

    public void wait(MemoryStack stack) {
        wait(stack, instance.defaultTimeout);
    }

    public synchronized void wait(MemoryStack stack, long timeout) {
        if (signaled) return;
        if (submissionTime == 0) throw new IllegalStateException("Fence is not signaled, nor pending");

        assertVkSuccess(vkWaitForFences(
                instance.vkDevice(), stack.longs(vkFence), true, timeout
        ), "WaitForFences", "FatFence");
        signaled = true;
        submissionTime = 0;
    }

    public synchronized void waitIfSubmitted(MemoryStack stack, long timeout) {
        if (submissionTime == 0) return;
        wait(stack, timeout);
    }

    public synchronized void waitIfSubmitted(MemoryStack stack) {
        waitIfSubmitted(stack, instance.defaultTimeout);
    }

    public void waitAndReset(MemoryStack stack) {
        waitAndReset(stack, instance.defaultTimeout);
    }

    public void waitAndReset(MemoryStack stack, long timeout) {
        wait(stack, timeout);
        reset(stack);
    }

    public synchronized boolean isSignaled() {
        isPending();
        return signaled;
    }

    public void destroy() {
        vkDestroyFence(instance.vkDevice(), vkFence, null);
    }

    @Override
    public int compareTo(VkbFence other) {
        return Long.compare(this.vkFence, other.vkFence);
    }
}
