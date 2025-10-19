package com.github.knokko.boiler.synchronization;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.memory.callbacks.CallbackUserData;
import org.lwjgl.vulkan.VkFenceCreateInfo;

import java.util.concurrent.ConcurrentSkipListSet;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/**
 * Represents a 'bank' from which you can borrow <i>VkbFence</i>s, and return them when you no longer need them.
 * You should <b>not</b> create an instance of this class, but instead access it via <i>boilerInstance.sync.fenceBank</i>
 */
public class FenceBank {

	private final BoilerInstance instance;
	private final ConcurrentSkipListSet<VkbFence> returnedFences = new ConcurrentSkipListSet<>();
	private final ConcurrentSkipListSet<VkbFence> borrowedFences = new ConcurrentSkipListSet<>();

	FenceBank(BoilerInstance instance) {
		this.instance = instance;
	}

	private synchronized VkbFence recycleFence() {
		var iterator = returnedFences.iterator();
		while (iterator.hasNext()) {
			var candidate = iterator.next();
			if (!candidate.isPending()) {
				iterator.remove();
				return candidate;
			}
		}
		return null;
	}

	/**
	 * Borrows a fence from this bank. If this bank doesn't have any available fences, a new one will be created.
	 * You should return the fence using <i>returnFence</i> when you no longer need it.
	 * @param startSignaled True if you want the fence to be initially signalled, false if not
	 * @param name The debug name of the fence (when <i>VK_EXT_debug_utils</i> is enabled)
	 * @return The borrowed fence
	 */
	public VkbFence borrowFence(boolean startSignaled, String name) {
		var fence = recycleFence();

		try (var stack = stackPush()) {
			if (fence == null) {
				var ciFence = VkFenceCreateInfo.calloc(stack);
				ciFence.sType$Default();
				ciFence.flags(startSignaled ? VK_FENCE_CREATE_SIGNALED_BIT : 0);

				var pFence = stack.callocLong(1);
				assertVkSuccess(vkCreateFence(
						instance.vkDevice(), ciFence, CallbackUserData.FENCE.put(stack, instance), pFence
				), "CreateFence", name);
				fence = new VkbFence(instance, pFence.get(0), startSignaled);
			} else {
				if (startSignaled) fence.signal();
				else fence.reset();
			}

			fence.setName(name, stack);
		}

		borrowedFences.add(fence);
		return fence;
	}

	/**
	 * Borrows multiple fences from this bank. If this bank doesn't have enough available fences, some new fences will
	 * be created. You should return the fences using <i>returnFence</i> when you no longer need it.
	 * @param startSignaled True if you want the fences to be initially signalled, false if not
	 * @param name The debug name of the fences (when <i>VK_EXT_debug_utils</i> is enabled)
	 * @return The borrowed fences
	 */
	public VkbFence[] borrowFences(int amount, boolean startSignaled, String name) {
		var fences = new VkbFence[amount];
		for (int index = 0; index < amount; index++) {
			fences[index] = this.borrowFence(startSignaled, name + "-" + index);
		}
		return fences;
	}

	/**
	 * <p>
	 *     Returns a fence to this bank that was previously borrowed using <i>borrowFence</i> or <i>borrowFences</i>.
	 * </p>
	 *
	 * <p>
	 *     After calling this method, you must <b>not</b> submit any new work to this fence, but you are free to await
	 *     or check any {@link FenceSubmission} for this fence.
	 * </p>
	 *
	 * <p>
	 *     You are allowed to return fences that are still <i>signaled</i> or <i>pending</i>. When you return a pending
	 *     fence, this bank will not lend it out until it has been signaled. When you return a signaled fence, it will
	 *     automatically be <i>reset</i> when the fence is lent out (unless the {@code startSignaled} parameter is
	 *     {@code true}).
	 * </p>
	 */
	public void returnFence(VkbFence fence) {
		if (!borrowedFences.remove(fence)) {
			throw new IllegalArgumentException("This fence wasn't borrowed");
		}
		returnedFences.add(fence);
	}

	/**
	 * Returns some fence to this bank that were previously borrowed using <i>borrowFence</i> or <i>borrowFences</i>
	 */
	public void returnFences(VkbFence... fences) {
		for (VkbFence fence : fences) returnFence(fence);
	}

	/**
	 * Note: this method is <b>not</b> thread-safe!
	 * Do not call it while borrowing or returning fences!
	 */
	public void awaitSubmittedFences() {
		for (var fence : borrowedFences) fence.waitIfSubmitted();
		for (var fence : returnedFences) fence.waitIfSubmitted();
	}

	/**
	 * This method will be called during <i>BoilerInstance.destroy</i>, so you should normally <b>not</b> call this
	 * method yourself!
	 */
	public void destroy() {
		if (!borrowedFences.isEmpty()) {
			int counter = 0;
			for (var fence : borrowedFences) {
				counter += 1;
				System.err.println("Fence " + fence + " was borrowed, but not returned");
				if (counter > 5) break;
			}
			throw new IllegalStateException("Not all borrowed fences have been returned");
		}
		try (var stack = stackPush()) {
			for (var fence : returnedFences) fence.destroy(stack);
		}
		returnedFences.clear();
	}
}
