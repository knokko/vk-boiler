package com.github.knokko.boiler.synchronization;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.memory.callbacks.CallbackUserData;
import org.lwjgl.system.MemoryStack;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/**
 * A fat wrapper around a <i>VkFence</i>. This class stores extra state to make the fence more powerful:
 * <ul>
 *     <li>You can also 'signal' it from the host (using the <i>signal()</i> method)</li>
 *     <li>
 *         You can use pass an instance of this class to the <i>FenceSubmission</i> constructor. You can safely await
 *         this fence submission, even after this fence has been awaited and reset, and even after it has been returned
 *         to the bank.
 *     </li>
 *     <li>
 *         Calling the instance methods of this class typically requires lesser code than calling the
 *         corresponding Vulkan functions.
 *     </li>
 * </ul>
 * You should <b>not</b> create instances of this class yourself: you should borrow them from
 * <i>boilerInstance.sync.fenceBank</i>.
 */
public class VkbFence implements Comparable<VkbFence> {

	private final BoilerInstance instance;
	private final long vkFence;

	private long currentTime = 1L;
	private long lastCompletedSubmission;
	private boolean isPending;

	private String debugName;

	VkbFence(BoilerInstance instance, long vkFence, boolean startSignaled) {
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

	@Override
	public String toString() {
		return "VkbFence(name=" + debugName + ",pending=" + isPending +
				",time=" + currentTime + ",finished=" + lastCompletedSubmission + ")";
	}
	/**
	 * Sets the debug name of this fence (if <i>VK_EXT_debug_utils</i> is enabled). Note that this method is called
	 * whenever you borrow a fence from the bank, so you should only call it yourself when you want to <b>change</b>
	 * the name.
	 * @param name The new debug name
	 * @param stack The memory stack onto which the <i>VkDebugUtilsObjectNameInfoEXT</i> should be allocated
	 */
	public void setName(String name, MemoryStack stack) {
		instance.debug.name(stack, vkFence, VK_OBJECT_TYPE_FENCE, name);
		debugName = name;
	}

	/**
	 * @return True if and only if this fence is currently pending
	 */
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
	 * Marks this fence as pending, and returns the VkFence handle. This method is used during <i>VkbQueue.submit</i>,
	 * and you should normally not need to call this method yourself. You should only use this when you submit the fence
	 * using <i>vkQueueSubmit</i> manually.
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

	/**
	 * Resets the fence. It must not be pending.
	 */
	public synchronized void reset() {
		if (isPending()) throw new IllegalStateException("Fence is still pending");

		try (var stack = stackPush()) {
			assertVkSuccess(vkResetFences(
					instance.vkDevice(), stack.longs(vkFence)
			), "ResetFences", "VkbFence.reset");
		}

		if (lastCompletedSubmission == currentTime) currentTime += 1;
	}

	/**
	 * Signals the fence. It must not be pending. Signalling a fence that is already signalled has no effect.
	 */
	public synchronized void signal() {
		if (isPending()) throw new IllegalStateException("Fence is still pending");
		lastCompletedSubmission = currentTime;
	}

	/**
	 * Forcibly resets this fence, which should usually only be done in unit tests.
	 */
	public synchronized void forceReset() {
		isPending = false;
		lastCompletedSubmission = currentTime;
		currentTime += 1;
	}

	/**
	 * Signals the fence forcibly, which should be done after a submission has failed.
	 */
	public synchronized void forceSignal() {
		isPending = false;
		lastCompletedSubmission = currentTime;
	}

	/**
	 * Waits until the fence is signalled. The fence must either be pending, or already signalled. The default
	 * timeout of the <i>BoilerInstance</i> may be passed to <i>vkWaitForFences</i>.
	 */
	public void awaitSignal() {
		awaitSignal(instance.defaultTimeout);
	}

	/**
	 * Waits until the fence is signalled. The fence must either be pending, or already signalled.
	 * @param timeout The timeout (in nanoseconds) that may be passed to <i>vkWaitForFences</i>
	 */
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

	/**
	 * Waits until the fence is signalled. If the fence is neither signalled, nor pending, returns immediately.
	 * @param timeout The timeout (in nanoseconds) that may be passed to <i>vkWaitForFences</i>
	 */
	public synchronized void waitIfSubmitted(long timeout) {
		if (!isPending) return;
		awaitSignal(timeout);
	}

	/**
	 * Waits until the fence is signalled. If the fence is neither signalled, nor pending, returns immediately.
	 * The default timeout of the <i>BoilerInstance</i> may be passed to <i>vkWaitForFences</i>.
	 */
	public synchronized void waitIfSubmitted() {
		waitIfSubmitted(instance.defaultTimeout);
	}

	/**
	 * Waits until the fence is signalled (possibly using the default timeout of the <i>BoilerInstance</i>), and resets
	 * the fence afterward. The fence must be either pending or signalled.
	 */
	public void waitAndReset() {
		waitAndReset(instance.defaultTimeout);
	}

	/**
	 * Waits until the fence is signalled, and resets the fence afterward. The fence must be either pending or
	 * signalled.
	 * @param timeout The timeout (in nanoseconds) that may be passed to <i>vkWaitForFences</i>
	 */
	public synchronized void waitAndReset(long timeout) {
		awaitSignal(timeout);
		reset();
	}

	synchronized void awaitSubmission(long referenceSubmissionTime) {
		if (lastCompletedSubmission >= referenceSubmissionTime) return;
		awaitSignal();
	}

	/**
	 * @return True if and only if the fence is currently signalled
	 */
	public synchronized boolean isSignaled() {
		return hasBeenSignaled(currentTime);
	}

	synchronized boolean hasBeenSignaled(long referenceSubmissionTime) {
		isPending();
		return lastCompletedSubmission >= referenceSubmissionTime;
	}

	void destroy(MemoryStack stack) {
		vkDestroyFence(instance.vkDevice(), vkFence, CallbackUserData.FENCE.put(stack, instance));
	}

	@Override
	public int compareTo(VkbFence other) {
		return Long.compare(this.vkFence, other.vkFence);
	}
}
