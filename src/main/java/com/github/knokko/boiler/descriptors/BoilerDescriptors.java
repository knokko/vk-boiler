package com.github.knokko.boiler.descriptors;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.buffers.VkbBufferRange;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memCallocLong;
import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.vulkan.VK10.*;

public class BoilerDescriptors {

	private final BoilerInstance instance;

	/**
	 * This constructor is meant for internal use only. You should use <i>boilerInstance.descriptors</i> instead.
	 */
	public BoilerDescriptors(BoilerInstance instance) {
		this.instance = instance;
	}

	/**
	 * Populates the binding at <i>index</i> into <i>bindings</i> with the given <i>descriptorType</i> and
	 * <i>stageFlags</i>, and with a <i>descriptorCount</i> of 1.
	 */
	public void binding(VkDescriptorSetLayoutBinding.Buffer bindings, int index, int descriptorType, int stageFlags) {
		var binding = bindings.get(index);
		binding.binding(index);
		binding.descriptorType(descriptorType);
		binding.descriptorCount(1);
		binding.stageFlags(stageFlags);
		binding.pImmutableSamplers(null);
	}

	/**
	 * Calls <i>vkCreateDescriptorSetLayout</i>, and wraps the result into a <i>VkbDescriptorSetLayout</i>
	 * @param stack The memory stack onto which <i>bindings</i> are allocated
	 * @param bindings The descriptor set layout bindings
	 * @param name The debug name (only used when <i>VK_EXT_debug_utils</i> is enabled)
	 * @return the wrapped descriptor set layout
	 */
	public VkbDescriptorSetLayout createLayout(MemoryStack stack, VkDescriptorSetLayoutBinding.Buffer bindings, String name) {
		return new VkbDescriptorSetLayout(stack, bindings, instance, name);
	}

	/**
	 * Calls <i>vkAllocateDescriptorSets</i>
	 * @param descriptorPool The descriptor pool from which the descriptor sets should be allocated
	 * @param name The debug name of the allocated descriptor sets (when <i>VK_EXT_debug_utils</i> is enabled)
	 * @param layouts The layouts of the descriptor sets. 1 descriptor set will be allocated for each element in
	 *                <i>layouts</i>
	 * @return The allocated descriptor sets
	 */
	public long[] allocate(long descriptorPool, String name, long... layouts) {
		try (var stack = stackPush()) {
			boolean useHeap = layouts.length > 10;

			var pSetLayouts = useHeap ? memCallocLong(layouts.length).put(0, layouts) : stack.longs(layouts);
			var aiSets = VkDescriptorSetAllocateInfo.calloc(stack);
			aiSets.sType$Default();
			aiSets.descriptorPool(descriptorPool);
			aiSets.pSetLayouts(pSetLayouts);

			var pSets = useHeap ? memCallocLong(layouts.length) : stack.callocLong(layouts.length);
			assertVkSuccess(vkAllocateDescriptorSets(
					instance.vkDevice(), aiSets, pSets
			), "AllocateDescriptorSets", name);

			long[] results = new long[layouts.length];
			for (int index = 0; index < results.length; index++) {
				long set = pSets.get(index);
				if (instance.debug.hasDebug) {
					try (var innerStack = stackPush()) {
						instance.debug.name(innerStack, set, VK_OBJECT_TYPE_DESCRIPTOR_SET, name);
					}
				}
				results[index] = set;
			}

			if (useHeap) {
				memFree(pSetLayouts);
				memFree(pSets);
			}
			return results;
		}
	}

	/**
	 * Creates an array of <i>VkDescriptorBufferInfo</i> structs that represent the given <i>bufferRanges</i>
	 * @param stack The memory stack onto which the structs should be allocated
	 * @param bufferRanges The buffer ranges that should be stored in the structs
	 * @return The resulting <i>VkDescriptorBufferInfo</i>s
	 */
	@SuppressWarnings("resource")
	public VkDescriptorBufferInfo.Buffer bufferInfo(MemoryStack stack, VkbBufferRange... bufferRanges) {
		var descriptorBufferInfo = VkDescriptorBufferInfo.calloc(bufferRanges.length, stack);
		for (int index = 0; index < bufferRanges.length; index++) {
			descriptorBufferInfo.get(index).buffer(bufferRanges[index].buffer().vkBuffer());
			descriptorBufferInfo.get(index).offset(bufferRanges[index].offset());
			descriptorBufferInfo.get(index).range(bufferRanges[index].size());
		}

		return descriptorBufferInfo;
	}

	/**
	 * Populates a <i>VkWriteDescriptorSet</i> such that it will write a buffer (range).
	 * @param stack The memory stack onto which the buffer range should be allocated
	 * @param descriptorWrites The <i>VkWriteDescriptorSet</i>s that should be populated
	 * @param descriptorSet The descriptor set to which the buffer range should be written
	 * @param binding The binding <b>and index</b> into <i>descriptorWrites</i>
	 * @param type The <i>VkDescriptorType</i>
	 * @param bufferRange The buffer (range) that should be written to the descriptor set
	 */
	public void writeBuffer(
			MemoryStack stack, VkWriteDescriptorSet.Buffer descriptorWrites,
			long descriptorSet, int binding, int type, VkbBufferRange bufferRange
	) {
		var write = descriptorWrites.get(binding);
		write.sType$Default();
		write.dstSet(descriptorSet);
		write.dstBinding(binding);
		write.dstArrayElement(0);
		write.descriptorCount(1);
		write.descriptorType(type);
		write.pBufferInfo(bufferInfo(stack, bufferRange));
	}

	/**
	 * Populates a <i>VkWriteDescriptorSet</i> such that it will write an image info
	 * @param descriptorWrites The <i>VkWriteDescriptorSet</i>s that should be populated
	 * @param descriptorSet The descriptor set to which the image info should be written
	 * @param binding The binding <b>and index</b> into <i>descriptorWrites</i>
	 * @param type The <i>VkDescriptorType</i>
	 * @param image The value that should be assigned to the <i>pImageInfo</i> field
	 */
	public void writeImage(
			VkWriteDescriptorSet.Buffer descriptorWrites,
			long descriptorSet, int binding, int type, VkDescriptorImageInfo.Buffer image
	) {
		var write = descriptorWrites.get(binding);
		write.sType$Default();
		write.dstSet(descriptorSet);
		write.dstBinding(binding);
		write.dstArrayElement(0);
		write.descriptorCount(image.remaining());
		write.descriptorType(type);
		write.pImageInfo(image);
	}
}
