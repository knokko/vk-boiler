package com.github.knokko.boiler.memory.callbacks;

/**
 * An example/debug implementation of {@link VkbAllocationCallbacks} that prints every driver allocation to
 * {@code System.out}.
 */
public class PrintAllocationCallbacks extends VkbAllocationCallbacks {

	@Override
	protected long allocate(long userData, long size, long alignment, int scope) {
		long result = super.allocate(userData, size, alignment, scope);
		System.out.println("allocate " + size + " bytes with " + alignment + " alignment for " +
				CallbackUserData.description(userData) + " (" +result + ")");
		return result;
	}

	@Override
	protected long relocate(long userData, long oldAllocation, long size, long alignment, int scope) {
		long newAllocation = super.relocate(userData, oldAllocation, size, alignment, scope);
		System.out.println("relocate to " + size + " for " + CallbackUserData.description(userData) +
				": old alloc was " + oldAllocation + ", new alloc is " + newAllocation);
		return newAllocation;
	}

	@Override
	protected void free(long userData, long allocation) {
		System.out.println("free " + allocationSizes.get(allocation) + " bytes for " +
				CallbackUserData.description(userData) + " (" + allocation + ")");
		super.free(userData, allocation);
	}

	@Override
	protected void internalAllocationCallback(long userData, long size, int type, int scope) {
		System.out.println("INTERNAL: allocate " + size + " bytes for " + CallbackUserData.description(userData));
	}

	@Override
	protected void internalFreeCallback(long userData, long size, int type, int scope) {
		System.out.println("INTERNAL: free " + size + " bytes for " + CallbackUserData.description(userData));
	}
}
