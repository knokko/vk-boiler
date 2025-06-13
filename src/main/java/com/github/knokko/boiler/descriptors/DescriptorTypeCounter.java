package com.github.knokko.boiler.descriptors;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.memory.callbacks.CallbackUserData;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;

import java.util.Arrays;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.vulkan.VK10.*;

class DescriptorTypeCounter {

	private final boolean mutable;
	private final int[] data = new int[30];

	DescriptorTypeCounter() {
		this.mutable = true;
		Arrays.fill(data, -1);
	}

	private DescriptorTypeCounter(int[] data) {
		this.mutable = false;
		System.arraycopy(data, 0, this.data, 0, data.length);
	}

	DescriptorTypeCounter copy() {
		return new DescriptorTypeCounter(data);
	}

	void add(int descriptorType, int amount) {
		for (int index = 0; index < data.length; index += 2) {
			if (data[index] == descriptorType) {
				data[index + 1] += amount;
				return;
			}
			if (data[index] == -1) {
				data[index] = descriptorType;
				data[index + 1] = amount;
				return;
			}
		}
		throw new RuntimeException("Too many distinct descriptor types; should not happen!");
	}

	void add(DescriptorTypeCounter other, int amount) {
		if (!mutable) throw new UnsupportedOperationException("This counter is immutable");
		for (int index = 0; index < other.data.length; index += 2) {
			if (other.data[index] == -1) return;
			add(other.data[index], amount * other.data[index + 1]);
		}
	}

	int get(int descriptorType) {
		for (int index = 0; index < data.length; index += 2) {
			if (data[index] == descriptorType) return data[index + 1];
			if (data[index] == -1) return 0;
		}
		return -1;
	}

	long createPool(BoilerInstance instance, MemoryStack stack, String name) {
		int size = 0;
		int total = 0;
		for (int index = 0; index < data.length; index += 2) {
			if (data[index] == -1) break;
			total += data[index + 1];
			size += 1;
		}
		if (size == 0) return VK_NULL_HANDLE;

		var poolSizes = VkDescriptorPoolSize.calloc(size, stack);
		for (int index = 0; index < size; index++) {
			//noinspection resource
			poolSizes.get(index).set(data[2 * index], data[2 * index + 1]);
		}

		var ciPool = VkDescriptorPoolCreateInfo.calloc(stack);
		ciPool.sType$Default();
		ciPool.maxSets(total);
		ciPool.pPoolSizes(poolSizes);

		var pPool = stack.callocLong(1);
		assertVkSuccess(vkCreateDescriptorPool(
				instance.vkDevice(), ciPool, CallbackUserData.DESCRIPTOR_POOL.put(stack, instance), pPool
		), "CreateDescriptorPool", name);
		long vkDescriptorPool = pPool.get(0);

		instance.debug.name(stack, vkDescriptorPool, VK_OBJECT_TYPE_DESCRIPTOR_POOL, name);

		return vkDescriptorPool;
	}
}
