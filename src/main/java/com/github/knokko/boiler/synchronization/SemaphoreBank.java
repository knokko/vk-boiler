package com.github.knokko.boiler.synchronization;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.memory.callbacks.CallbackUserData;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/**
 * A 'bank' from which you can borrow binary semaphores, and return them when you no longer need them.
 * You should <b>not</b> create an instance of this class: you should get the instance via
 * <i>boilerInstance.sync.semaphoreBank</i> instead.
 */
public class SemaphoreBank {

	private final BoilerInstance instance;
	private final ConcurrentSkipListSet<Long> unusedSemaphores = new ConcurrentSkipListSet<>();
	private final ConcurrentSkipListSet<Long> borrowedSemaphores = new ConcurrentSkipListSet<>();
	private final ConcurrentHashMap<Long, String> semaphoreNames = new ConcurrentHashMap<>();

	SemaphoreBank(BoilerInstance instance) {
		this.instance = instance;
	}

	/**
	 * Borrows a (binary) semaphore from the bank. If the bank doesn't have any semaphores left, a new one will be
	 * created. You should return it using <i>returnSemaphores</i> when you no longer need it.
	 * @param name The debug name of the semaphore (when <i>VK_EXT_debug_utils</i> is enabled)
	 * @return The borrowed semaphore
	 */
	public long borrowSemaphore(String name) {
		Long semaphore = unusedSemaphores.pollFirst();
		try (var stack = stackPush()) {
			if (semaphore == null) {
				var ciSemaphore = VkSemaphoreCreateInfo.calloc(stack);
				ciSemaphore.sType$Default();
				ciSemaphore.flags(0);

				var pSemaphore = stack.callocLong(1);
				assertVkSuccess(vkCreateSemaphore(
						instance.vkDevice(), ciSemaphore, CallbackUserData.SEMAPHORE.put(stack, instance), pSemaphore
				), "CreateSemaphore", name);
				semaphore = pSemaphore.get(0);
			}
			instance.debug.name(stack, semaphore, VK_OBJECT_TYPE_SEMAPHORE, name);
			semaphoreNames.put(semaphore, name);
		}
		borrowedSemaphores.add(semaphore);
		return semaphore;
	}

	/**
	 * Borrows <i>amount</i> (binary) semaphores from the bank. If the bank doesn't have enough semaphores left, some new
	 * semaphores will be created. You should return them using <i>returnSemaphores</i> when you no longer need them.
	 * @param name The debug name of the semaphores (when <i>VK_EXT_debug_utils</i> is enabled)
	 * @return The borrowed semaphores
	 */
	public long[] borrowSemaphores(int amount, String name) {
		long[] semaphores = new long[amount];
		for (int index = 0; index < amount; index++) semaphores[index] = this.borrowSemaphore(name + "-" + index);
		return semaphores;
	}

	/**
	 * Returns semaphores that were previously borrowed from this bank. The semaphores must <b>not</b> be pending.
	 */
	public void returnSemaphores(long... semaphores) {
		for (long semaphore : semaphores) {
			if (!borrowedSemaphores.remove(semaphore)) {
				throw new IllegalArgumentException("This semaphore wasn't borrowed");
			}
			semaphoreNames.remove(semaphore);
		}

		for (long semaphore : semaphores) unusedSemaphores.add(semaphore);
	}

	/**
	 * This method will be called during <i>BoilerInstance.destroy</i>, so you should <b>not</b> call this method
	 * yourself!
	 */
	public void destroy() {
		if (!borrowedSemaphores.isEmpty()) {
			int counter = 0;
			for (var semaphore : borrowedSemaphores) {
				counter += 1;
				System.err.println("Semaphore " + semaphoreNames.get(semaphore) + " was borrowed, but not returned");
				if (counter > 5) break;
			}
			throw new IllegalStateException("Not all borrowed semaphores have been returned");
		}
		try (var stack = stackPush()) {
			for (long semaphore : unusedSemaphores) {
				vkDestroySemaphore(instance.vkDevice(), semaphore, CallbackUserData.SEMAPHORE.put(stack, instance));
			}
		}
		unusedSemaphores.clear();
	}
}
