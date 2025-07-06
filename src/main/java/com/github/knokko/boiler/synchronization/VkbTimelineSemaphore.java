package com.github.knokko.boiler.synchronization;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.memory.callbacks.CallbackUserData;
import org.lwjgl.vulkan.VkSemaphoreSignalInfo;
import org.lwjgl.vulkan.VkSemaphoreWaitInfo;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRTimelineSemaphore.*;
import static org.lwjgl.vulkan.VK12.*;

/**
 * A wrapper class around a timeline semaphore. You can call
 * <ul>
 *     <li><i>waitUntil(value)</i>, which will use <i>vkWaitForSemaphores</i></li>
 *     <li><i>getValue()</i>, which will use <i>vkGetSemaphoreCounterValue</i></li>
 *     <li><i>setValue(newValue)</i>, which will use <i>vkSignalSemaphore</i></li>
 * </ul>
 * This is useful because calling these instance methods will take 1 line of code, whereas calling these Vulkan
 * functions yourself will probably take you more than 5 lines of code. You should use
 * <i>boilerInstance.sync.createTimelineSemaphore</i> to create instances of this class.
 */
public class VkbTimelineSemaphore {

	private final BoilerInstance instance;

	/**
	 * The wrapped <i>VkSemaphore</i>
	 */
	public final long vkSemaphore;
	private final String name;
	private final boolean usesTimelineSemaphoreExtension;

	VkbTimelineSemaphore(BoilerInstance instance, long vkSemaphore, String name) {
		this.instance = instance;
		this.vkSemaphore = vkSemaphore;
		this.name = name;
		this.usesTimelineSemaphoreExtension = instance.extra.deviceExtensions().contains(VK_KHR_TIMELINE_SEMAPHORE_EXTENSION_NAME);
	}

	/**
	 * Calls <i>vkWaitSemaphores</i> to wait until the semaphore reaches the given value. The default timeout of the
	 * <i>BoilerInstance</i> will be used.
	 */
	public void waitUntil(long value) {
		try (var stack = stackPush()) {
			var wiSemaphore = VkSemaphoreWaitInfo.calloc(stack);
			wiSemaphore.sType$Default();
			wiSemaphore.flags(0);
			wiSemaphore.semaphoreCount(1);
			wiSemaphore.pSemaphores(stack.longs(vkSemaphore));
			wiSemaphore.pValues(stack.longs(value));

			if (usesTimelineSemaphoreExtension) {
				assertVkSuccess(vkWaitSemaphoresKHR(
						instance.vkDevice(), wiSemaphore, instance.defaultTimeout
				), "WaitSemaphoresKHR", name);
			} else {
				assertVkSuccess(vkWaitSemaphores(
						instance.vkDevice(), wiSemaphore, instance.defaultTimeout
				), "WaitSemaphores", name);
			}
		}
	}

	/**
	 * Calls <i>vkGetSemaphoreCounterValue</i> to get the value of this semaphore
	 */
	public long getValue() {
		try (var stack = stackPush()) {
			var pValue = stack.callocLong(1);
			if (usesTimelineSemaphoreExtension) {
				assertVkSuccess(vkGetSemaphoreCounterValueKHR(
						instance.vkDevice(), vkSemaphore, pValue
				), "GetSemaphoreCounterValueKHR", name);
			} else {
				assertVkSuccess(vkGetSemaphoreCounterValue(
						instance.vkDevice(), vkSemaphore, pValue
				), "GetSemaphoreCounterValue", name);
			}
			return pValue.get(0);
		}
	}

	/**
	 * Calls <i>vkSignalSemaphore</i> to change the value of this semaphore to <i>newValue</i>
	 */
	public void setValue(long newValue) {
		try (var stack = stackPush()) {
			var siSemaphore = VkSemaphoreSignalInfo.calloc(stack);
			siSemaphore.sType$Default();
			siSemaphore.semaphore(vkSemaphore);
			siSemaphore.value(newValue);

			if (usesTimelineSemaphoreExtension) {
				assertVkSuccess(vkSignalSemaphoreKHR(
						instance.vkDevice(), siSemaphore
				), "SignalSemaphore", name);
			} else {
				assertVkSuccess(vkSignalSemaphore(
						instance.vkDevice(), siSemaphore
				), "SignalSemaphore", name);
			}
		}
	}

	/**
	 * Calls <i>vkDestroySemaphore</i> to destroy this semaphore
	 */
	public void destroy() {
		try (var stack = stackPush()) {
			vkDestroySemaphore(instance.vkDevice(), vkSemaphore, CallbackUserData.SEMAPHORE.put(stack, instance));
		}
	}
}
