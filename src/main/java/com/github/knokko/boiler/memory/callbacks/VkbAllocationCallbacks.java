package com.github.knokko.boiler.memory.callbacks;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Pointer;
import org.lwjgl.vulkan.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.github.knokko.boiler.utilities.BoilerMath.nextMultipleOf;
import static java.lang.Long.min;
import static java.lang.Math.max;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * <p>
 *   This class aims to simplify working with Vulkan Allocation Callbacks (the <b>pAllocator</b> parameter that goes to
 *   almost every Vulkan creation function). If you want to use this parameter with vk-boiler, you need to override this
 *   class, and pass an instance of your subclass to
 *   {@link com.github.knokko.boiler.builders.BoilerBuilder#allocationCallbacks(VkbAllocationCallbacks)}.
 * </p>
 * <p>
 *   The default implementations of all the callbacks use {@link org.lwjgl.system.MemoryUtil} to allocate all the memory
 *   requested by the driver. This is almost certainly inefficient, so you should only use the default implementations
 *   for testing purposes. You can take a look at {@link PrintAllocationCallbacks} or {@link SumAllocationCallbacks}
 *   for testing examples. The latter can also be used to estimate the amount of memory used by the driver.
 * </p>
 * <p>
 *
 * </p>
 */
public abstract class VkbAllocationCallbacks {

	/**
	 * Used by the default implementation to track the size of each memory allocation, which is needed to implement
	 * {@link #relocate(long, long, long, long, int)}.
	 */
	protected final Map<Long, Long> allocationSizes = new ConcurrentHashMap<>();

	protected long validAlignment(long alignment) {
		return max(Pointer.POINTER_SIZE, alignment);
	}

	/**
	 * Implements {@link VkAllocationCallbacks#pfnAllocation(VkAllocationFunctionI)}
	 */
	protected long allocate(long userData, long size, long alignment, int scope) {
		if (size == 0L) return alignment;

		alignment = validAlignment(alignment);
		size = nextMultipleOf(size, alignment);

		long allocation = nmemAlignedAlloc(alignment, size);
		if (allocation != 0L) allocationSizes.put(allocation, size);
		return allocation;
	}

	/**
	 * Implements {@link VkAllocationCallbacks#pfnReallocation(VkReallocationFunctionI)}
	 */
	protected long relocate(long userData, long oldAllocation, long size, long alignment, int scope) {
		if (size == 0L) {
			if (oldAllocation != 0L && allocationSizes.remove(oldAllocation) != null) nmemAlignedFree(oldAllocation);
			return alignment;
		}

		alignment = validAlignment(alignment);
		size = nextMultipleOf(size, alignment);

		long newAllocation = nmemAlignedAlloc(alignment, size);
		if (newAllocation == 0L) return 0L;

		if (oldAllocation != 0L) {
			Long oldSize = allocationSizes.remove(oldAllocation);
			if (oldSize != null) {
				memCopy(oldAllocation, newAllocation, min(oldSize, size));
				nmemAlignedFree(oldAllocation);
			}
		}
		allocationSizes.put(newAllocation, size);

		return newAllocation;
	}

	/**
	 * Implements {@link VkAllocationCallbacks#pfnFree(VkFreeFunctionI)}
	 */
	protected void free(long userData, long allocation) {
		if (allocation != 0L && allocationSizes.remove(allocation) != null) nmemAlignedFree(allocation);
	}

	/**
	 * Implements {@link VkAllocationCallbacks#pfnInternalAllocation(VkInternalAllocationNotificationI)}. The
	 * default implementation does nothing, but you can override it if you want to monitor it.
	 */
	protected void internalAllocationCallback(long userData, long size, int type, int scope) {}

	/**
	 * Implements {@link VkAllocationCallbacks#pfnInternalFree(VkInternalFreeNotificationI)}. The
	 * default implementation does nothing, but you can override it if you want to monitor it.
	 */
	protected void internalFreeCallback(long userData, long size, int type, int scope) {}

	/**
	 * Puts an {@link VkAllocationCallbacks} on the given {@link MemoryStack}, and sets all its callback fields to the
	 * implementations of this {@link VkbAllocationCallbacks}. Furthermore, sets
	 * {@link VkAllocationCallbacks#pUserData(long)} to {@code userData}.
	 */
	public VkAllocationCallbacks put(MemoryStack stack, long userData) {
		var callbacks = VkAllocationCallbacks.calloc(stack);
		callbacks.pUserData(userData);
		callbacks.pfnAllocation(this::allocate);
		callbacks.pfnReallocation(this::relocate);
		callbacks.pfnFree(this::free);
		callbacks.pfnInternalAllocation(this::internalAllocationCallback);
		callbacks.pfnInternalFree(this::internalFreeCallback);
		return callbacks;
	}

	public static class InvalidFreeException extends RuntimeException {

		private InvalidFreeException(long allocation) {
			super("Invalid free for non-existing allocation " + allocation);
		}
	}
}
