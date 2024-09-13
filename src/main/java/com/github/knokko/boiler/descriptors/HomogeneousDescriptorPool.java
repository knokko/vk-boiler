package com.github.knokko.boiler.descriptors;

import java.util.Arrays;

import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorPool;

/**
 * Wraps a <i>VkDescriptorPool</i> from which <i>VkDescriptorSets</i> with 1 specific <i>VkDescriptorSetLayout</i> can
 * be allocated. Use the <i>createPool</i> method of a <i>VkbDescriptorSetLayout</i> to create an instance of this
 * class.
 */
public class HomogeneousDescriptorPool {

	public final VkbDescriptorSetLayout layout;
	public final long vkDescriptorPool;
	private final String name;
	private long counter;

	HomogeneousDescriptorPool(VkbDescriptorSetLayout layout, long vkDescriptorPool, String name) {
		this.layout = layout;
		this.vkDescriptorPool = vkDescriptorPool;
		this.name = name;
	}

	/**
	 * Allocates and returns <i>amount</i> <i>VkDescriptorSet</i>s from this pool
	 */
	public long[] allocate(int amount) {
		long[] pLayouts = new long[amount];
		Arrays.fill(pLayouts, layout.vkDescriptorSetLayout);
		long[] result = layout.instance.descriptors.allocate(vkDescriptorPool, name + "-" + counter, pLayouts);
		counter += 1;
		return result;
	}

	/**
	 * Calls <i>vkDestroyDescriptorPool</i>
	 */
	public void destroy() {
		vkDestroyDescriptorPool(layout.instance.vkDevice(), vkDescriptorPool, null);
	}
}
