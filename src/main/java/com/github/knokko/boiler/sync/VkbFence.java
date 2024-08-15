package com.github.knokko.boiler.sync;

import com.github.knokko.boiler.instance.BoilerInstance;
import org.lwjgl.system.MemoryStack;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
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

    public long getSubmissionTime() {
        return submissionTime;
    }

    public synchronized void reset() {
        if (isPending()) throw new IllegalStateException("Fence is still pending");

        signaled = false;
        try (var stack = stackPush()) {
            assertVkSuccess(vkResetFences(
                    instance.vkDevice(), stack.longs(vkFence)
            ), "ResetFences", "VkbFence.reset");
        }
    }

    public synchronized void signal() {
        if (isPending()) throw new IllegalStateException("Fence is still pending");
        signaled = true;
    }

    public void awaitSignal() {
        awaitSignal(instance.defaultTimeout);
    }

    public synchronized void awaitSignal(long timeout) {
        if (signaled) return;
        if (submissionTime == 0) throw new IllegalStateException("Fence is not signaled, nor pending");

        try (var stack = stackPush()) {
            assertVkSuccess(vkWaitForFences(
                    instance.vkDevice(), stack.longs(vkFence), true, timeout
            ), "WaitForFences", "VkbFence.awaitSignal");
        }
        signaled = true;
        submissionTime = 0;
    }

    public synchronized void waitIfSubmitted(long timeout) {
        if (submissionTime == 0) return;
        awaitSignal(timeout);
    }

    public synchronized void waitIfSubmitted() {
        waitIfSubmitted(instance.defaultTimeout);
    }

    public void waitAndReset(MemoryStack stack) {
        waitAndReset(instance.defaultTimeout);
    }

    public synchronized void waitAndReset(long timeout) {
        awaitSignal(timeout);
        reset();
    }

    public synchronized void awaitSubmission(long referenceSubmissionTime) {
        if (submissionTime > referenceSubmissionTime) return;
        awaitSignal();
    }

    public synchronized boolean isSignaled() {
        return hasBeenSignaled(submissionTime);
    }

    public synchronized boolean hasBeenSignaled(long referenceSubmissionTime) {
        isPending();
        return signaled || submissionTime > referenceSubmissionTime;
    }

    void destroy() {
        vkDestroyFence(instance.vkDevice(), vkFence, null);
    }

    @Override
    public int compareTo(VkbFence other) {
        return Long.compare(this.vkFence, other.vkFence);
    }
}
