package com.github.knokko.boiler.synchronization;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.memory.callbacks.CallbackUserData;
import org.lwjgl.vulkan.*;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.*;

public class BoilerSync {

	private final BoilerInstance instance;

	/**
	 * You can borrow and return fences from this bank
	 */
	public final FenceBank fenceBank;

	/**
	 * You can borrow and return binary semaphores from this bank
	 */
	public final SemaphoreBank semaphoreBank;

	/**
	 * This constructor is meant for internal use only. You should use <i>boilerInstance.sync</i> instead.
	 */
	public BoilerSync(BoilerInstance instance) {
		this.instance = instance;
		this.fenceBank = new FenceBank(instance);
		this.semaphoreBank = new SemaphoreBank(instance);
	}

	/**
	 * Creates and returns a timeline semaphore with the given initial value
	 * @param initialValue The initial value of the timeline semaphore
	 * @param name The debug name of the timeline semaphore (when <i>VK_EXT_debug_utils</i> is enabled)
	 * @return The wrapped timeline semaphore
	 */
	public VkbTimelineSemaphore createTimelineSemaphore(long initialValue, String name) {
		try (var stack = stackPush()) {
			var ciTimeline = VkSemaphoreTypeCreateInfo.calloc(stack);
			ciTimeline.sType$Default();
			ciTimeline.semaphoreType(VK_SEMAPHORE_TYPE_TIMELINE);
			ciTimeline.initialValue(initialValue);

			var ciSemaphore = VkSemaphoreCreateInfo.calloc(stack);
			ciSemaphore.sType$Default();
			ciSemaphore.pNext(ciTimeline);
			ciSemaphore.flags(0);

			var pSemaphore = stack.callocLong(1);
			assertVkSuccess(vkCreateSemaphore(
					instance.vkDevice(), ciSemaphore, CallbackUserData.SEMAPHORE.put(stack, instance), pSemaphore
			), "CreateSemaphore", name);
			long vkSemaphore = pSemaphore.get(0);
			instance.debug.name(stack, vkSemaphore, VK_OBJECT_TYPE_SEMAPHORE, name);

			return new VkbTimelineSemaphore(instance, vkSemaphore, name);
		}
	}
}
