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

	public FixedDescriptorBank(VkbDescriptorSetLayout layout, int capacity, int flags, String name) {
		try (var stack = stackPush()) {
			this.pool = layout.createPool(capacity, flags, name);
			long[] descriptorSets = this.pool.allocate(stack, capacity);

			this.unusedDescriptorSets = new ConcurrentSkipListSet<>();
			for (long descriptorSet : descriptorSets) this.unusedDescriptorSets.add(descriptorSet);
			this.borrowedDescriptorSets = new ConcurrentSkipListSet<>();
		}
	}

	/**
	 * Note: this method returns null when all descriptor sets are currently borrowed.
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

	public void returnDescriptorSet(long descriptorSet) {
		if (!borrowedDescriptorSets.remove(descriptorSet)) {
			throw new IllegalArgumentException(descriptorSet + " wasn't borrowed");
		}
		unusedDescriptorSets.add(descriptorSet);
	}

	public void destroy(boolean checkEmpty) {
		if (checkEmpty && !borrowedDescriptorSets.isEmpty()) {
			throw new IllegalStateException("Not all descriptor sets have been returned");
		}
		pool.destroy();
	}
}
