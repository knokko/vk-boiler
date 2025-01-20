package com.github.knokko.boiler.descriptors;

import com.github.knokko.boiler.BoilerInstance;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;

import java.util.HashMap;
import java.util.Map;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.VK10.*;

/**
 * To create a {@link SharedDescriptorPool}, you need to use a {@link SharedDescriptorPoolBuilder}.
 *
 * <p>
 *   You need to tell the {@link SharedDescriptorPoolBuilder} how many descriptors you need for each descriptor
 *   set layout, using its {@link #request} method.
 * </p>
 *
 * <p>
 *   Once you're done, you can call its {@link #build} method to create a
 *   {@link SharedDescriptorPool} from which you can allocate exactly as many descriptor sets as you requested.
 * </p>
 */
public class SharedDescriptorPoolBuilder {

	private final BoilerInstance instance;
	private final Map<Integer, Integer> countMapping = new HashMap<>();
	private final Map<VkbDescriptorSetLayout, Integer> layoutMapping = new HashMap<>();
	private int numberOfSets = 0;

	public SharedDescriptorPoolBuilder(BoilerInstance instance) {
		this.instance = instance;
	}

	/**
	 * Reserves space for {@code numberOfSets} sets whose descriptor set layout is {@code layout}. You are allowed to
	 * call this method more than once with the same {@code layout}.
	 */
	public synchronized void request(VkbDescriptorSetLayout layout, int numberOfSets) {
		this.numberOfSets += numberOfSets;
		layout.descriptorTypeCounts.forEach((descriptorType, count) -> {
			countMapping.put(descriptorType, countMapping.getOrDefault(descriptorType, 0) + count * numberOfSets);
		});
		layoutMapping.put(layout, layoutMapping.getOrDefault(layout, 0) + numberOfSets);
	}

	/**
	 * Creates a {@link SharedDescriptorPool} that has exactly enough space to allocate all descriptor sets that were
	 * requested via {@link #request}.
	 * @param name The debug name of the <i>VkDescriptorPool</i> to be created
	 */
	public SharedDescriptorPool build(String name) {
		if (numberOfSets == 0) return new SharedDescriptorPool(VK_NULL_HANDLE, new HashMap<>());
		try (var stack = stackPush()) {
			var poolSizes = VkDescriptorPoolSize.calloc(countMapping.size(), stack);
			countMapping.forEach((descriptorType, amount) -> {
				//noinspection resource
				poolSizes.get().set(descriptorType, amount);
			});
			poolSizes.position(0);

			var ciPool = VkDescriptorPoolCreateInfo.calloc(stack);
			ciPool.sType$Default();
			ciPool.maxSets(numberOfSets);
			ciPool.pPoolSizes(poolSizes);

			var pPool = stack.callocLong(1);
			assertVkSuccess(vkCreateDescriptorPool(
					instance.vkDevice(), ciPool, null, pPool
			), "CreateDescriptorPool", name);
			long vkDescriptorPool = pPool.get(0);

			var pSetLayouts = memCallocLong(numberOfSets);
			layoutMapping.forEach((layout, amount) -> {
				for (int counter = 0; counter < amount; counter++) pSetLayouts.put(layout.vkDescriptorSetLayout);
			});
			pSetLayouts.flip();

			var pDescriptorSets = memCallocLong(numberOfSets);
			var aiDescriptorSets = VkDescriptorSetAllocateInfo.calloc(stack);
			aiDescriptorSets.sType$Default();
			aiDescriptorSets.descriptorPool(vkDescriptorPool);
			aiDescriptorSets.pSetLayouts(pSetLayouts);

			assertVkSuccess(vkAllocateDescriptorSets(
					instance.vkDevice(), aiDescriptorSets, pDescriptorSets
			), "AllocateDescriptorSets", name);

			Map<VkbDescriptorSetLayout, long[]> descriptorSets = new HashMap<>(layoutMapping.size());
			layoutMapping.forEach((layout, amount) -> {
				long[] sets = new long[amount];
				for (int index = 0; index < amount; index++) {
					sets[index] = pDescriptorSets.get();
				}
				descriptorSets.put(layout, sets);
			});

			memFree(pSetLayouts);
			memFree(pDescriptorSets);

			return new SharedDescriptorPool(vkDescriptorPool, descriptorSets);
		}
	}
}
