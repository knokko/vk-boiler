package com.github.knokko.boiler.memory.callbacks;

import com.github.knokko.boiler.BoilerInstance;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkAllocationCallbacks;

/**
 * This enum has a constant for most Vulkan objects, and the ordinals of this enum are used as the {@code userData}
 * parameter for the Vulkan Allocation Callbacks by vk-boiler. When the
 * {@link com.github.knokko.boiler.builders.BoilerBuilder} is created with allocation callbacks, the {@code userData}
 * of each invocation will be {@code 1 + callbackUserData.ordinal()}.
 */
public enum CallbackUserData {
	INSTANCE,
	DEBUG_MESSENGER,
	SURFACE,
	DEVICE,
	SAMPLER,
	DESCRIPTOR_SET_LAYOUT,
	DESCRIPTOR_POOL,
	RENDER_PASS,
	PIPELINE_LAYOUT,
	PIPELINE,
	SEMAPHORE,
	FENCE,
	MEMORY,
	VMA,
	IMAGE,
	IMAGE_VIEW,
	BUFFER,
	COMMAND_POOL,
	FRAME_BUFFER,
	SWAPCHAIN,
	SHADER_MODULE;

	private static final CallbackUserData[] VALUES = values();

	/**
	 * Extracts the Vulkan object name/description from the {@code userData} passed to an allocation callback, assuming
	 * that {@code userData = 1 + callbackUserData.ordinal()}
	 * @return The corresponding enum constant name, or simply {@code Long.toString(userData)} when {@code userData}
	 * has an unexpected value.
	 */
	public static String description(long userData) {
		if (userData > 0L && userData <= VALUES.length) return VALUES[(int) userData - 1].name();
		return Long.toString(userData);
	}

	/**
	 * If {@code callbacks != null}, invokes {@link VkbAllocationCallbacks#put}, encoding this {@link CallbackUserData}
	 * in the {@code userData}. If {@code callbacks == null}, returns {@code null}
	 */
	public VkAllocationCallbacks put(MemoryStack stack, VkbAllocationCallbacks callbacks) {
		if (callbacks == null) return null;
		else return callbacks.put(stack, 1 + ordinal());
	}

	/**
	 * If {@code instance.allocationCallbacks != null}, invokes {@link VkbAllocationCallbacks#put}, encoding this
	 * {@link CallbackUserData} in the {@code userData}. If {@code instance.allocationCallbacks == null},
	 * returns {@code null}
	 */
	public VkAllocationCallbacks put(MemoryStack stack, BoilerInstance instance) {
		return put(stack, instance.allocationCallbacks);
	}
}
