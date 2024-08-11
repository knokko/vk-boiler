package com.github.knokko.boiler.descriptors;

import com.github.knokko.boiler.instance.BoilerInstance;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;

import java.util.HashMap;
import java.util.Map;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class DescriptorSetLayout {

	final BoilerInstance instance;
	public final long vkDescriptorSetLayout;

	final Map<Integer, Integer> descriptorTypeCounts = new HashMap<>();

	DescriptorSetLayout(MemoryStack stack, VkDescriptorSetLayoutBinding.Buffer bindings, BoilerInstance instance, String name) {
		this.instance = instance;

		var ciLayout = VkDescriptorSetLayoutCreateInfo.calloc(stack);
		ciLayout.sType$Default();
		ciLayout.flags(0);
		ciLayout.pBindings(bindings);

		var pLayout = stack.callocLong(1);
		assertVkSuccess(vkCreateDescriptorSetLayout(
				instance.vkDevice(), ciLayout, null, pLayout
		), "CreateDescriptorSetLayout", name);
		this.vkDescriptorSetLayout = pLayout.get(0);

		instance.debug.name(stack, this.vkDescriptorSetLayout, VK_OBJECT_TYPE_DESCRIPTOR_SET_LAYOUT, name);

		for (int index = bindings.position(); index < bindings.limit(); index++) {
			descriptorTypeCounts.put(bindings.get(index).descriptorType(), 0);
		}
		for (int index = bindings.position(); index < bindings.limit(); index++) {
			int type = bindings.get(index).descriptorType();
			descriptorTypeCounts.put(type, descriptorTypeCounts.get(type) + bindings.get(index).descriptorCount());
		}
	}

	public HomogeneousDescriptorPool createPool(int maxSets, int flags, String name) {
		try (var stack = stackPush()) {

			var poolSizes = VkDescriptorPoolSize.calloc(descriptorTypeCounts.size(), stack);
			int poolSizeIndex = 0;
			for (var entry : descriptorTypeCounts.entrySet()) {
				var size = poolSizes.get(poolSizeIndex);
				size.type(entry.getKey());
				size.descriptorCount(maxSets * entry.getValue());
				poolSizeIndex += 1;
			}

			var ciPool = VkDescriptorPoolCreateInfo.calloc(stack);
			ciPool.sType$Default();
			ciPool.flags(flags);
			ciPool.maxSets(maxSets);
			ciPool.pPoolSizes(poolSizes);

			var pPool = stack.callocLong(1);
			assertVkSuccess(vkCreateDescriptorPool(
					instance.vkDevice(), ciPool, null, pPool
			), "CreateDescriptorPool", name);
			var pool = pPool.get(0);

			instance.debug.name(stack, pool, VK_OBJECT_TYPE_DESCRIPTOR_POOL, name);
			return new HomogeneousDescriptorPool(this, pool, name);
		}
	}

	public void destroy() {
		vkDestroyDescriptorSetLayout(instance.vkDevice(), vkDescriptorSetLayout, null);
	}
}
