package com.github.knokko.boiler.synchronization;

import com.github.knokko.boiler.BoilerInstance;
import org.lwjgl.vulkan.VkSemaphoreSignalInfo;
import org.lwjgl.vulkan.VkSemaphoreWaitInfo;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRTimelineSemaphore.*;
import static org.lwjgl.vulkan.VK12.*;

public class VkbTimelineSemaphore {

	private final BoilerInstance instance;
	public final long vkSemaphore;
	private final String name;
	private final boolean usesTimelineSemaphoreExtension;

	public VkbTimelineSemaphore(BoilerInstance instance, long vkSemaphore, String name) {
		this.instance = instance;
		this.vkSemaphore = vkSemaphore;
		this.name = name;
		this.usesTimelineSemaphoreExtension = instance.deviceExtensions.contains(VK_KHR_TIMELINE_SEMAPHORE_EXTENSION_NAME);
	}

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

	public void destroy() {
		vkDestroySemaphore(instance.vkDevice(), vkSemaphore, null);
	}
}
