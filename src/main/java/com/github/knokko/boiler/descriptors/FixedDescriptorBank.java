package com.github.knokko.boiler.descriptors;

import java.util.concurrent.ConcurrentSkipListSet;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/**
 * <p>
 * A <i>FixedDescriptorBank</i> is a descriptor pool from which descriptor sets of one specific descriptor set layout
 * can be 'borrowed'. All descriptor sets are allocated right after creation of the descriptor pool, and are never
 * freed or reset. Instead, descriptor sets can be 'borrowed' and 'returned', after which they can be 'borrowed' again.
 * </p>
 *
 * <p>
 * <i>FixedDescriptorBank</i>s are 'fixed' in the sense that they have a bounded capacity specified in the
 * constructor, and can't grow any bigger. You can use <i>GrowingDescriptorBank</i> instead if you don't like this
 * limitation.
 * </p>
 * <p>
 * Borrowing and returning descriptor sets is thread-safe, but destroying the bank is not: the bank can only be
 * destroyed when all borrows and returns have been completed.
 */
public class FixedDescriptorBank {

	private final HomogeneousDescriptorPool pool;

	private final ConcurrentSkipListSet<Long> borrowedDescriptorSets;
	private final ConcurrentSkipListSet<Long> unusedDescriptorSets;

	/**
	 * @param layout All descriptor sets borrowed from this bank will get this layout
	 * @param capacity The maximum number of descriptor sets that can be borrowed from this bank at the same time
	 * @param flags The <i>VkDescriptorPoolCreateFlags</i>
	 * @param name The debug name of the descriptor pool (when <i>VK_EXT_debug_utils</i> is enabled)
	 */
	public FixedDescriptorBank(VkbDescriptorSetLayout layout, int capacity, int flags, String name) {
		this.pool = layout.createPool(capacity, flags, name);
		long[] descriptorSets = this.pool.allocate(capacity);

		this.unusedDescriptorSets = new ConcurrentSkipListSet<>();
		for (long descriptorSet : descriptorSets) this.unusedDescriptorSets.add(descriptorSet);
		this.borrowedDescriptorSets = new ConcurrentSkipListSet<>();
	}

	/**
	 * Borrows a descriptor set from this bank
	 * @param name The debug name of the descriptor set (when <i>VK_EXT_debug_utils</i> is enabled)
	 * @return The borrowed <i>VkDescriptorSet</i>, or <i>null</i> when the maximum number of descriptor sets is
	 * currently borrowed from this bank
	 */
	public Long borrowDescriptorSet(String name) {
		Long result = unusedDescriptorSets.pollFirst();
		if (result != null) {
			borrowedDescriptorSets.add(result);
			try (var stack = stackPush()) {
				pool.layout.instance.debug.name(stack, result, VK_OBJECT_TYPE_DESCRIPTOR_SET, name);
			}
		}
		return result;
	}

	/**
	 * Returns the given <i>VkDescriptorSet</i> to this bank
	 */
	public void returnDescriptorSet(long descriptorSet) {
		if (!borrowedDescriptorSets.remove(descriptorSet)) {
			throw new IllegalArgumentException(descriptorSet + " wasn't borrowed");
		}
		unusedDescriptorSets.add(descriptorSet);
	}

	/**
	 * Destroys this bank, including its descriptor pool
	 * @param checkEmpty When true, an exception will be thrown if not all descriptor sets have been returned
	 *                   (potentially useful for detecting leaks)
	 */
	public void destroy(boolean checkEmpty) {
		if (checkEmpty && !borrowedDescriptorSets.isEmpty()) {
			throw new IllegalStateException("Not all descriptor sets have been returned");
		}
		pool.destroy();
	}
}
