package com.github.knokko.boiler.memory.callbacks;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A simple implementation of {@link VkbAllocationCallbacks} that tracks how much memory the driver uses to store
 * each type of Vulkan object ({@link CallbackUserData}). You can read the amounts using {@link #copySimpleSizes()}
 * or {@link #copyInternalSizes()}. If you need something more specialized, write your own implementation of
 * {@link VkbAllocationCallbacks}.
 */
public class SumAllocationCallbacks extends VkbAllocationCallbacks {

	private final Map<Long, AtomicLong> simpleAllocationSums = new ConcurrentHashMap<>();
	private final Map<Long, AtomicLong> internalAllocationSums = new ConcurrentHashMap<>();

	@Override
	protected long allocate(long userData, long size, long alignment, int scope) {
		simpleAllocationSums.computeIfAbsent(userData, key -> new AtomicLong(0)).addAndGet(size);
		return super.allocate(userData, size, alignment, scope);
	}

	@Override
	protected long relocate(long userData, long oldAllocation, long size, long alignment, int scope) {
		simpleAllocationSums.computeIfAbsent(userData, key -> new AtomicLong(0)).addAndGet(
				size - allocationSizes.getOrDefault(oldAllocation, 0L)
		);
		return super.relocate(userData, oldAllocation, size, alignment, scope);
	}

	@Override
	protected void free(long userData, long allocation) {
		simpleAllocationSums.computeIfAbsent(
				userData, key -> new AtomicLong(0)
		).addAndGet(-allocationSizes.getOrDefault(allocation, 0L));
		super.free(userData, allocation);
	}

	@Override
	protected void internalAllocationCallback(long userData, long size, int type, int scope) {
		internalAllocationSums.computeIfAbsent(userData, key -> new AtomicLong(0)).addAndGet(size);
	}

	@Override
	protected void internalFreeCallback(long userData, long size, int type, int scope) {
		internalAllocationSums.computeIfAbsent(
				userData, key -> new AtomicLong(0)
		).addAndGet(-size);
		System.out.println("INTERNAL: free " + size + " bytes for " + CallbackUserData.description(userData));
	}

	/**
	 * Copies the number of bytes allocated for each value of {@code userData}, excluding 'internal'/special driver
	 * allocations.
	 */
	public Map<Long, Long> copySimpleSizes() {
		var copy = new HashMap<Long, Long>();
		simpleAllocationSums.forEach((userData, sum) -> copy.put(userData, sum.get()));
		return copy;
	}

	/**
	 * Copies the number of bytes 'internally' allocated for each value of {@code userData}. Note that not all drivers
	 * do internal allocations. For instance, my driver does not.
	 */
	public Map<Long, Long> copyInternalSizes() {
		var copy = new HashMap<Long, Long>();
		internalAllocationSums.forEach((userData, sum) -> copy.put(userData, sum.get()));
		return copy;
	}
}
