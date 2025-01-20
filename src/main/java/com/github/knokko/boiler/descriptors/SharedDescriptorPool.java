package com.github.knokko.boiler.descriptors;

import com.github.knokko.boiler.BoilerInstance;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorPool;

/**
 * A descriptor pool from which a predefined number of descriptor sets from different descriptor set layouts can
 * be allocated. Use {@link SharedDescriptorPoolBuilder} to create instances of this class.
 */
public class SharedDescriptorPool {

	private final long vkDescriptorPool;
	private final Map<VkbDescriptorSetLayout, Entry> entryMap = new HashMap<>();

	SharedDescriptorPool(long vkDescriptorPool, Map<VkbDescriptorSetLayout, long[]> descriptorSets) {
		this.vkDescriptorPool = vkDescriptorPool;
		descriptorSets.forEach((layout, sets) -> entryMap.put(layout, new Entry(sets)));
	}

	public synchronized long[] allocate(VkbDescriptorSetLayout layout, int amount) {
		Entry entry = entryMap.get(layout);
		if (entry == null) throw new IllegalArgumentException("This layout wasn't request()ed by the builder");

		int nextIndex = entry.nextIndex + amount;
		if (nextIndex > entry.descriptorSets.length) {
			throw new IllegalArgumentException("Too few descriptor sets of this type were request()ed by the builder");
		}

		long[] result = Arrays.copyOfRange(entry.descriptorSets, entry.nextIndex, nextIndex);
		entry.nextIndex = nextIndex;
		return result;
	}

	public void destroy(BoilerInstance instance) {
		vkDestroyDescriptorPool(instance.vkDevice(), vkDescriptorPool, null);
	}

	private static class Entry {

		final long[] descriptorSets;
		int nextIndex;

		Entry(long[] descriptorSets) {
			this.descriptorSets = descriptorSets;
		}
	}
}
