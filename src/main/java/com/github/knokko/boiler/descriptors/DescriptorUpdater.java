package com.github.knokko.boiler.descriptors;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.buffers.VkbBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.VK10.*;

/**
 * This is a thin wrapper around {@link VkWriteDescriptorSet.Buffer} that can be used to update descriptor sets with
 * less boilerplate code. Usage:
 * <ol>
 *     <li>Create an instance of this class using the public constructor.</li>
 *     <li>
 *         Call the <i>write...()</i> methods to conveniently modify {@link #descriptorWrites}. If something is not
 *         covered by the <i>write...()</i> methods, you can modify {@link #descriptorWrites} manually.
 *     </li>
 *     <li>Call {@link #update} to call {@link org.lwjgl.vulkan.VK10#vkUpdateDescriptorSets}.</li>
 * </ol>
 */
public class DescriptorUpdater {

	public final MemoryStack stack;
	public final VkWriteDescriptorSet.Buffer descriptorWrites;

	/**
	 * @param numWrites The capacity of the {@link VkWriteDescriptorSet.Buffer}
	 */
	public DescriptorUpdater(MemoryStack stack, int numWrites) {
		this.stack = stack;
		this.descriptorWrites = VkWriteDescriptorSet.calloc(numWrites, stack);
	}

	/**
	 * Modifies the {@link VkWriteDescriptorSet} at the given index. Sets:
	 * <ul>
	 *     <li>{@link VkWriteDescriptorSet#sType()} to the default structure type</li>
	 *     <li>{@link VkWriteDescriptorSet#dstSet()} to {@code vkDescriptorSet}</li>
	 *     <li>{@link VkWriteDescriptorSet#dstBinding()} to {@code binding}</li>
	 *     <li>{@link VkWriteDescriptorSet#descriptorCount()} to 1</li>
	 *     <li>{@link VkWriteDescriptorSet#descriptorType()} to {@code descriptorType}</li>
	 * </ul>
	 * Note that you should usually call more specialized methods like {@link #writeImage} or
	 * {@link #writeUniformBuffer} instead.
	 */
	public void write(int index, long vkDescriptorSet, int binding, int descriptorType) {
		var write = descriptorWrites.get(index);
		write.sType$Default();
		write.dstSet(vkDescriptorSet);
		write.dstBinding(binding);
		write.descriptorCount(1);
		write.descriptorType(descriptorType);
	}

	/**
	 * Modifies the {@link VkWriteDescriptorSet} at the given index. Sets:
	 * <ul>
	 *     <li>{@link VkWriteDescriptorSet#sType()} to the default structure type</li>
	 *     <li>{@link VkWriteDescriptorSet#dstSet()} to {@code vkDescriptorSet}</li>
	 *     <li>{@link VkWriteDescriptorSet#dstBinding()} to {@code binding}</li>
	 *     <li>{@link VkWriteDescriptorSet#descriptorCount()} to 1</li>
	 *     <li>{@link VkWriteDescriptorSet#descriptorType()} to {@code descriptorType}</li>
	 *     <li>{@link VkWriteDescriptorSet#pBufferInfo()} to the buffer segment described by {@code buffer}</li>
	 * </ul>
	 * Note that you should usually call {@link #writeUniformBuffer} or {@link #writeStorageBuffer} instead.
	 */
	public void writeBuffer(int index, long vkDescriptorSet, int binding, int descriptorType, VkbBuffer buffer) {
		write(index, vkDescriptorSet, binding, descriptorType);
		var bufferInfo = VkDescriptorBufferInfo.calloc(1, stack);
		//noinspection resource
		bufferInfo.get(0).set(buffer.vkBuffer, buffer.offset, buffer.size);

		var write = descriptorWrites.get(index);
		write.pBufferInfo(bufferInfo);
	}

	/**
	 * Modifies the {@link VkWriteDescriptorSet} at the given index. Sets:
	 * <ul>
	 *     <li>{@link VkWriteDescriptorSet#sType()} to the default structure type</li>
	 *     <li>{@link VkWriteDescriptorSet#dstSet()} to {@code vkDescriptorSet}</li>
	 *     <li>{@link VkWriteDescriptorSet#dstBinding()} to {@code binding}</li>
	 *     <li>{@link VkWriteDescriptorSet#descriptorCount()} to 1</li>
	 *     <li>{@link VkWriteDescriptorSet#descriptorType()} to {@link VK10#VK_DESCRIPTOR_TYPE_STORAGE_BUFFER}</li>
	 *     <li>{@link VkWriteDescriptorSet#pBufferInfo()} to the buffer segment described by {@code storageBuffer}</li>
	 * </ul>
	 */
	public void writeStorageBuffer(int index, long vkDescriptorSet, int binding, VkbBuffer storageBuffer) {
		writeBuffer(index, vkDescriptorSet, binding, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, storageBuffer);
	}

	/**
	 * Modifies the {@link VkWriteDescriptorSet} at the given index. Sets:
	 * <ul>
	 *     <li>{@link VkWriteDescriptorSet#sType()} to the default structure type</li>
	 *     <li>{@link VkWriteDescriptorSet#dstSet()} to {@code vkDescriptorSet}</li>
	 *     <li>{@link VkWriteDescriptorSet#dstBinding()} to {@code binding}</li>
	 *     <li>{@link VkWriteDescriptorSet#descriptorCount()} to 1</li>
	 *     <li>{@link VkWriteDescriptorSet#descriptorType()} to {@link VK10#VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER}</li>
	 *     <li>{@link VkWriteDescriptorSet#pBufferInfo()} to the buffer segment described by {@code uniformBuffer}</li>
	 * </ul>
	 */
	public void writeUniformBuffer(int index, long vkDescriptorSet, int binding, VkbBuffer uniformBuffer) {
		writeBuffer(index, vkDescriptorSet, binding, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, uniformBuffer);
	}

	/**
	 * Modifies the {@link VkWriteDescriptorSet} at the given index. Sets:
	 * <ul>
	 *     <li>{@link VkWriteDescriptorSet#sType()} to the default structure type</li>
	 *     <li>{@link VkWriteDescriptorSet#dstSet()} to {@code vkDescriptorSet}</li>
	 *     <li>{@link VkWriteDescriptorSet#dstBinding()} to {@code binding}</li>
	 *     <li>{@link VkWriteDescriptorSet#descriptorCount()} to 1</li>
	 *     <li>{@link VkWriteDescriptorSet#descriptorType()} to {@link VK10#VK_DESCRIPTOR_TYPE_SAMPLER}</li>
	 *     <li>{@link VkWriteDescriptorSet#pImageInfo()} to ({@code vkSampler}, 0, 0)</li>
	 * </ul>
	 */
	public void writeSampler(int index, long vkDescriptorSet, int binding, long vkSampler) {
		write(index, vkDescriptorSet, binding, VK_DESCRIPTOR_TYPE_SAMPLER);

		var imageInfo = VkDescriptorImageInfo.calloc(1, stack);
		//noinspection resource
		imageInfo.get(0).set(vkSampler, VK_NULL_HANDLE, VK_IMAGE_LAYOUT_UNDEFINED);

		descriptorWrites.get(index).pImageInfo(imageInfo);
	}

	/**
	 * Modifies the {@link VkWriteDescriptorSet} at the given index. Sets:
	 * <ul>
	 *     <li>{@link VkWriteDescriptorSet#sType()} to the default structure type</li>
	 *     <li>{@link VkWriteDescriptorSet#dstSet()} to {@code vkDescriptorSet}</li>
	 *     <li>{@link VkWriteDescriptorSet#dstBinding()} to {@code binding}</li>
	 *     <li>{@link VkWriteDescriptorSet#descriptorCount()} to 1</li>
	 *     <li>
	 *         {@link VkWriteDescriptorSet#descriptorType()} to {@link VK10#VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE} or
	 *         {@link VK10#VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER}, depending on whether {@code vkSampler == 0}
	 *     </li>
	 *     <li>
	 *         {@link VkWriteDescriptorSet#pImageInfo()} to ({@code vkSampler}, {@code vkImageView},
	 *         {@link VK10#VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL})
	 *     </li>
	 * </ul>
	 */
	public void writeImage(int index, long vkDescriptorSet, int binding, long vkImageView, long vkSampler) {
		int descriptorType = vkSampler == VK_NULL_HANDLE ? VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE : VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
		write(index, vkDescriptorSet, binding, descriptorType);

		var imageInfo = VkDescriptorImageInfo.calloc(1, stack);
		//noinspection resource
		imageInfo.get(0).set(vkSampler, vkImageView, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

		descriptorWrites.get(index).pImageInfo(imageInfo);
	}

	/**
	 * Calls {@link VK10#vkUpdateDescriptorSets} using {@link #descriptorWrites}
	 */
	public void update(BoilerInstance instance) {
		vkUpdateDescriptorSets(instance.vkDevice(), descriptorWrites, null);
	}
}
