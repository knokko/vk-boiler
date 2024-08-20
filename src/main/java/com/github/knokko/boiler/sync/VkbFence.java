package com.github.knokko.boiler.sync;

import com.github.knokko.boiler.BoilerInstance;
import org.lwjgl.system.MemoryStack;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class VkbFence implements Comparable<VkbFence> {

	private final BoilerInstance instance;
	private final long vkFence;

	private long currentTime = 1L;
	private long lastCompletedSubmission;
	private boolean isPending;

	public VkbFence(BoilerInstance instance, long vkFence, boolean startSignaled) {
		this.instance = instance;
		this.vkFence = vkFence;
		if (startSignaled) lastCompletedSubmission = 1L;
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
		if (!isPending) return false;

		var status = vkGetFenceStatus(instance.vkDevice(), vkFence);
		if (status == VK_NOT_READY) return true;
		assertVkSuccess(status, "GetFenceStatus", "BoilerFence.isPending");

		currentTime += 1;
		lastCompletedSubmission = currentTime;
		isPending = false;
		return false;
	}

	/**
	 * Marks this fence as pending, and returns the VkFence handle.
	 * You should use this when you submit the fence manually.
	 */
	public synchronized long getVkFenceAndSubmit() {
		if (isPending) throw new IllegalStateException("This fence is already pending");
		if (lastCompletedSubmission >= currentTime) throw new IllegalStateException("This fence is still signaled");
		isPending = true;
		return vkFence;
	}

	synchronized long getCurrentTime() {
		return currentTime;
	}

	public synchronized void reset() {
		if (isPending()) throw new IllegalStateException("Fence is still pending");

		try (var stack = stackPush()) {
			assertVkSuccess(vkResetFences(
					instance.vkDevice(), stack.longs(vkFence)
			), "ResetFences", "VkbFence.reset");
		}

		if (lastCompletedSubmission == currentTime) currentTime += 1;
	}

	public synchronized void signal() {
		if (isPending()) throw new IllegalStateException("Fence is still pending");
		lastCompletedSubmission = currentTime;
	}

	public void awaitSignal() {
		awaitSignal(instance.defaultTimeout);
	}

	public synchronized void awaitSignal(long timeout) {
		if (lastCompletedSubmission == currentTime) return;
		if (!isPending) throw new IllegalStateException("Fence is not signaled, nor pending");

		try (var stack = stackPush()) {
			assertVkSuccess(vkWaitForFences(
					instance.vkDevice(), stack.longs(vkFence), true, timeout
			), "WaitForFences", "VkbFence.awaitSignal");
		}
		lastCompletedSubmission = currentTime;
		isPending = false;
	}

	public synchronized void waitIfSubmitted(long timeout) {
		if (!isPending) return;
		awaitSignal(timeout);
	}

	public synchronized void waitIfSubmitted() {
		waitIfSubmitted(instance.defaultTimeout);
	}

	public void waitAndReset() {
		waitAndReset(instance.defaultTimeout);
	}

	public synchronized void waitAndReset(long timeout) {
		awaitSignal(timeout);
		reset();
	}

	public synchronized void awaitSubmission(long referenceSubmissionTime) {
		if (lastCompletedSubmission >= referenceSubmissionTime) return;
		awaitSignal();
	}

	public synchronized boolean isSignaled() {
		return hasBeenSignaled(currentTime);
	}

	public synchronized boolean hasBeenSignaled(long referenceSubmissionTime) {
		isPending();
		return lastCompletedSubmission >= referenceSubmissionTime;
	}

	void destroy() {
		vkDestroyFence(instance.vkDevice(), vkFence, null);
	}

	@Override
	public int compareTo(VkbFence other) {
		return Long.compare(this.vkFence, other.vkFence);
	}
}
