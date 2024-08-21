package com.github.knokko.boiler.synchronization;

import com.github.knokko.boiler.BoilerInstance;
import org.lwjgl.vulkan.*;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.*;

public class BoilerSync {

	private final BoilerInstance instance;
	public final FenceBank fenceBank;
	public final SemaphoreBank semaphoreBank;

	public BoilerSync(BoilerInstance instance) {
		this.instance = instance;
		this.fenceBank = new FenceBank(instance);
		this.semaphoreBank = new SemaphoreBank(instance);
	}

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
					instance.vkDevice(), ciSemaphore, null, pSemaphore
			), "CreateSemaphore", name);
			long vkSemaphore = pSemaphore.get(0);
			instance.debug.name(stack, vkSemaphore, VK_OBJECT_TYPE_SEMAPHORE, name);

			return new VkbTimelineSemaphore(instance, vkSemaphore, name);
		}
	}
}
