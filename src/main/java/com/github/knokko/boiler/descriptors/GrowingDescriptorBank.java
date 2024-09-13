package com.github.knokko.boiler.descriptors;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListSet;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/**
 * <p>
 * A <i>GrowingDescriptorBank</i> is a 'bank' of descriptor sets in the sense that users can borrow descriptor sets
 * from it, and return those once they are no longer needed. Once allocated, descriptor sets from the bank will
 * never be freed or reset: when they are returned, they can simply be borrowed again. When the bank is destroyed,
 * all descriptor pools will be destroyed.
 * </p>
 *
 * <p>
 * Unlike <i>FixedDescriptorBank</i>s, <i>GrowingDescriptorBank</i>s have unlimited capacity: when all descriptor
 * sets have been borrowed, it will create an additional (bigger) descriptor pool. But, just like fixed banks,
 * growing banks also support only descriptor sets from 1 descriptor set layout.
 * </p>
 * <p>
 * Borrowing and returning descriptor sets is thread-safe, but destroying the bank is not: the bank can only be
 * destroyed when all borrows and returns have been completed.
 */
public class GrowingDescriptorBank {

	private final VkbDescriptorSetLayout layout;
	private final int flags;

	private final List<HomogeneousDescriptorPool> descriptorPools = new ArrayList<>();
	private int nextCapacity = 2;

	private final ConcurrentSkipListSet<Long> unusedDescriptorSets, borrowedDescriptorSets;

	/**
	 * @param layout All descriptor sets borrowed from this bank will get this layout
	 * @param flags The <i>VkDescriptorPoolCreateFlags</i>
	 */
	public GrowingDescriptorBank(VkbDescriptorSetLayout layout, int flags) {
		this.layout = layout;
		this.flags = flags;
		this.unusedDescriptorSets = new ConcurrentSkipListSet<>();
		this.borrowedDescriptorSets = new ConcurrentSkipListSet<>();
	}

	/**
	 * Borrows a descriptor set from this bank
	 * @param name The debug name of the descriptor set (when <i>VK_EXT_debug_utils</i> is enabled)
	 * @return The borrowed <i>VkDescriptorSet</i>
	 */
	public long borrowDescriptorSet(String name) {
		Long maybeResult = unusedDescriptorSets.pollFirst();
		if (maybeResult == null) {
			synchronized (this) {

				// Ensure that it's not possible to create 2 new pools at the same time
				maybeResult = unusedDescriptorSets.pollFirst();
				if (maybeResult == null) {

					var newDescriptorPool = layout.createPool(nextCapacity, flags, name + "-" + nextCapacity);

					descriptorPools.add(newDescriptorPool);

					var newSets = newDescriptorPool.allocate(nextCapacity);
					maybeResult = newSets[0];
					for (int index = 1; index < nextCapacity; index++) unusedDescriptorSets.add(newSets[index]);

					nextCapacity *= 2;
				}
			}
		}
		borrowedDescriptorSets.add(maybeResult);
		try (var stack = stackPush()) {
			layout.instance.debug.name(stack, maybeResult, VK_OBJECT_TYPE_DESCRIPTOR_SET, name);
		}
		return maybeResult;
	}

	/**
	 * Returns the given <i>VkDescriptorSet</i> to this bank
	 */
	public void returnDescriptorSet(long descriptorSet) {
		if (!borrowedDescriptorSets.remove(descriptorSet)) {
			throw new IllegalArgumentException("Descriptor set " + descriptorSet + " wasn't borrowed");
		}
		unusedDescriptorSets.add(descriptorSet);
	}

	/**
	 * Destroys this bank, including all its descriptor pools
	 * @param checkBorrows When true, an exception will be thrown if not all descriptor sets have been returned
	 *                   (potentially useful for detecting leaks)
	 */
	public void destroy(boolean checkBorrows) {
		if (checkBorrows && !borrowedDescriptorSets.isEmpty()) {
			throw new IllegalStateException("Not all borrowed descriptor sets have been returned");
		}
		for (var descriptorPool : descriptorPools) {
			descriptorPool.destroy();
		}
	}
}
