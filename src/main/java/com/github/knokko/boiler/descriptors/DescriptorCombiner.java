package com.github.knokko.boiler.descriptors;

import com.github.knokko.boiler.BoilerInstance;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongConsumer;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memCallocLong;
import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.vkAllocateDescriptorSets;

/**
 * This class makes it easy to create a <b>VkDescriptorPool</b> that contains exactly the right number of descriptors
 * of each descriptor type to allocate the requested number of descriptor sets for each requested descriptor set layout.
 * Usage:
 * <ol>
 *     <li>Create an instance of this class using the public constructor.</li>
 *     <li>
 *         Call the {@link #addSingle} or {@link #addMultiple} method for each descriptor set layout for which you
 *         would like to allocate descriptor sets. Note that you need to use {@link DescriptorSetLayoutBuilder} to
 *         create the descriptor set layouts.
 *     </li>
 *     <li>
 *         Call the {@link #build} method to create the descriptor pool, and allocate all the requested descriptor sets.
 *     </li>
 * </ol>
 */
public class DescriptorCombiner {

	private final BoilerInstance instance;
	private final Map<VkbDescriptorSetLayout, Requests> requests = new HashMap<>();
	private final DescriptorTypeCounter descriptorTypes = new DescriptorTypeCounter();

	public DescriptorCombiner(BoilerInstance instance) {
		this.instance = instance;
	}

	/**
	 * Requests a single descriptor set with the given descriptor set layout.
	 * @param layout The desired descriptor set layout
	 * @param callback The callback that will be invoked when {@link #build} is called. The allocated
	 *                    <b>VkDescriptorSet</b> will be sent to the callback.
	 */
	public void addSingle(VkbDescriptorSetLayout layout, LongConsumer callback) {
		descriptorTypes.add(layout.descriptorTypes, 1);
		requests.computeIfAbsent(layout, key -> new Requests()).solo.add(callback);
	}

	/**
	 * Requests {@code amount} descriptor sets with the given descriptor set layout.
	 * @param layout The desired descriptor set layout
	 * @param amount The number of descriptor sets to be allocated
	 * @return a <b>long[]</b> of length {@code amount}, initially filled with zeros. When {@link #build} is called,
	 * this array will be filled with the allocated <b>VkDescriptorSet</b> handles.
	 */
	public long[] addMultiple(VkbDescriptorSetLayout layout, int amount) {
		descriptorTypes.add(layout.descriptorTypes, amount);
		long[] descriptorSets = new long[amount];
		requests.computeIfAbsent(layout, key -> new Requests()).bulk.add(descriptorSets);
		return descriptorSets;
	}

	/**
	 * Creates the <b>VkDescriptorPool</b>, and allocates all requested descriptor sets. All callbacks passed to
	 * {@link #addSingle} will be invoked, and all <b>long[]</b>s returned by {@link #addMultiple} will be populated.
	 * @param name The debug name of the descriptor pool (when validation is enabled)
	 * @return the created <b>VkDescriptorPool</b>
	 */
	public long build(String name) {
		try (MemoryStack stack = stackPush()) {
			long vkDescriptorPool = descriptorTypes.createPool(instance, stack, name);
			if (vkDescriptorPool == VK_NULL_HANDLE) return vkDescriptorPool;

			int total = 0;
			for (Requests requests : this.requests.values()) total += requests.count();

			// Do NOT use MemoryStack because it may or may not fit on the stack
			var pSetLayouts = memCallocLong(total);
			var pDescriptorSets = memCallocLong(total);

			for (var entry : requests.entrySet()) {
				int count = entry.getValue().count();
				while (count > 0) {
					pSetLayouts.put(entry.getKey().vkDescriptorSetLayout);
					count -= 1;
				}
			}
			pSetLayouts.flip();

			var aiDescriptorSets = VkDescriptorSetAllocateInfo.calloc(stack);
			aiDescriptorSets.sType$Default();
			aiDescriptorSets.descriptorPool(vkDescriptorPool);
			aiDescriptorSets.pSetLayouts(pSetLayouts);

			assertVkSuccess(vkAllocateDescriptorSets(
					instance.vkDevice(), aiDescriptorSets, pDescriptorSets
			), "AllocateDescriptorSets", name);

			for (var requests : this.requests.values()) {
				for (long[] bulk : requests.bulk()) {
					for (int bulkIndex = 0; bulkIndex < bulk.length; bulkIndex++) {
						bulk[bulkIndex] = pDescriptorSets.get();
					}
				}
				for (LongConsumer solo : requests.solo()) {
					solo.accept(pDescriptorSets.get());
				}
			}

			memFree(pSetLayouts);
			memFree(pDescriptorSets);
			return vkDescriptorPool;
		}
	}

	private record Requests(Collection<long[]> bulk, Collection<LongConsumer> solo) {

		Requests() {
			this(new ArrayList<>(), new ArrayList<>());
		}

		int count() {
			int total = solo.size();
			for (long[] sets : bulk) total += sets.length;
			return total;
		}
	}
}
