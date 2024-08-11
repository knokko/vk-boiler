package com.github.knokko.boiler.descriptors;

import org.lwjgl.system.MemoryStack;

import java.util.Arrays;

import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorPool;

public class HomogeneousDescriptorPool {

	public final DescriptorSetLayout layout;
	public final long vkDescriptorPool;
	private final String name;
	private long counter;

	HomogeneousDescriptorPool(DescriptorSetLayout layout, long vkDescriptorPool, String name) {
		this.layout = layout;
		this.vkDescriptorPool = vkDescriptorPool;
		this.name = name;
	}

	public long[] allocate(MemoryStack stack, int amount) {
		long[] pLayouts = new long[amount];
		Arrays.fill(pLayouts, layout.vkDescriptorSetLayout);
		long[] result = layout.instance.descriptors.allocate(
				stack, amount, vkDescriptorPool, name + "-" + counter, pLayouts
		);
		counter += 1;
		return result;
	}

	public void destroy() {
		vkDestroyDescriptorPool(layout.instance.vkDevice(), vkDescriptorPool, null);
	}
}
