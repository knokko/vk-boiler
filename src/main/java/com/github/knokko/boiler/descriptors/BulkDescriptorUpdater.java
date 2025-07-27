package com.github.knokko.boiler.descriptors;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.buffers.VkbBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.VK10.*;

/**
 * <p>
 *     This is a variant of {@link DescriptorUpdater} that is more suitable for doing 'bulk' descriptor set updates
 *     (updating many descriptor sets at the same time with each call to {@link VK10#vkUpdateDescriptorSets}).
 * </p>
 * <p>
 *     Unlike {@link DescriptorUpdater}, this class does <i>not</i> need to know the number of descriptor updates ahead
 *     of time, and you <i>do not have to</i> allocate all structs on a {@link MemoryStack} (by passing {@code null} to
 *     the 'stack' parameter of the constructor).
 * </p>
 * <p>
 *     Usage:
 * </p>
 * <ol>
 *     <li>Create an instance of this class using the public constructor.</li>
 *     <li>Call methods like {@link #writeUniformBuffer} and {@link #writeImage} as often as you need.</li>
 *     <li>Call {@link #finish()}</li>
 * </ol>
 * <p>
 *     The constructor parameters determine how much capacity is allocated for the descriptor write structures, and
 *     whether they will be allocated on the stack. When the capacity is not large enough to update all descriptor sets
 *     at the same time, multiple calls to {@link VK10#vkUpdateDescriptorSets} will be made. This happens automatically
 *     whenever the limit is reached.
 * </p>
 */
public class BulkDescriptorUpdater {

	private final BoilerInstance instance;
	private final boolean usedStack;
	private final VkWriteDescriptorSet.Buffer descriptorWrites;
	private final VkDescriptorBufferInfo.Buffer bufferWrites;
	private final VkDescriptorImageInfo.Buffer imageWrites;

	/**
	 * Constructs a new {@link BulkDescriptorUpdater} with the given capacity.
	 * @param stack The {@link MemoryStack} onto which the {@link VkWriteDescriptorSet} (and related) structures will
	 *              be allocated, or {@code null} to use <b>calloc</b> (without stack) instead. Since stacks typically
	 *              have only 64kb capacity, you should pass {@code null} when the capacities are large.
	 * @param capacity The number of {@link VkWriteDescriptorSet} structures that will be allocated
	 * @param bufferCapacity The number of {@link VkDescriptorBufferInfo} structures that will be allocated
	 * @param imageCapacity The number of {@link VkDescriptorImageInfo} structures that will be allocated
	 */
	public BulkDescriptorUpdater(
			BoilerInstance instance, MemoryStack stack,
			int capacity, int bufferCapacity, int imageCapacity
	) {
		this.instance = instance;
		this.usedStack = stack != null;
		if (stack != null) {
			descriptorWrites = VkWriteDescriptorSet.calloc(capacity, stack);
			bufferWrites = VkDescriptorBufferInfo.calloc(bufferCapacity, stack);
			imageWrites = VkDescriptorImageInfo.calloc(imageCapacity, stack);
		} else {
			descriptorWrites = VkWriteDescriptorSet.calloc(capacity);
			bufferWrites = VkDescriptorBufferInfo.calloc(bufferCapacity);
			imageWrites = VkDescriptorImageInfo.calloc(imageCapacity);
		}
	}

	/**
	 * Modifies the next {@link VkWriteDescriptorSet} . Sets:
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
	public void write(long vkDescriptorSet, int binding, int descriptorType) {
		if (!descriptorWrites.hasRemaining()) flush();
		var write = descriptorWrites.get();
		write.sType$Default();
		write.dstSet(vkDescriptorSet);
		write.dstBinding(binding);
		write.descriptorCount(1);
		write.descriptorType(descriptorType);
	}

	/**
	 * Modifies the next {@link VkWriteDescriptorSet}. Sets:
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
	public void writeBuffer(long vkDescriptorSet, int binding, int descriptorType, VkbBuffer buffer) {
		if (!bufferWrites.hasRemaining()) flush();
		write(vkDescriptorSet, binding, descriptorType);

		var bufferInfo = bufferWrites.get();
		bufferInfo.set(buffer.vkBuffer, buffer.offset, buffer.size);

		var write = descriptorWrites.get(descriptorWrites.position() - 1);
		write.pBufferInfo(VkDescriptorBufferInfo.create(bufferInfo.address(), 1));
	}

	/**
	 * Modifies the next {@link VkWriteDescriptorSet}. Sets:
	 * <ul>
	 *     <li>{@link VkWriteDescriptorSet#sType()} to the default structure type</li>
	 *     <li>{@link VkWriteDescriptorSet#dstSet()} to {@code vkDescriptorSet}</li>
	 *     <li>{@link VkWriteDescriptorSet#dstBinding()} to {@code binding}</li>
	 *     <li>{@link VkWriteDescriptorSet#descriptorCount()} to 1</li>
	 *     <li>{@link VkWriteDescriptorSet#descriptorType()} to {@link VK10#VK_DESCRIPTOR_TYPE_STORAGE_BUFFER}</li>
	 *     <li>{@link VkWriteDescriptorSet#pBufferInfo()} to the buffer segment described by {@code storageBuffer}</li>
	 * </ul>
	 */
	public void writeStorageBuffer(long vkDescriptorSet, int binding, VkbBuffer storageBuffer) {
		writeBuffer(vkDescriptorSet, binding, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, storageBuffer);
	}

	/**
	 * Modifies the next {@link VkWriteDescriptorSet}. Sets:
	 * <ul>
	 *     <li>{@link VkWriteDescriptorSet#sType()} to the default structure type</li>
	 *     <li>{@link VkWriteDescriptorSet#dstSet()} to {@code vkDescriptorSet}</li>
	 *     <li>{@link VkWriteDescriptorSet#dstBinding()} to {@code binding}</li>
	 *     <li>{@link VkWriteDescriptorSet#descriptorCount()} to 1</li>
	 *     <li>{@link VkWriteDescriptorSet#descriptorType()} to {@link VK10#VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER}</li>
	 *     <li>{@link VkWriteDescriptorSet#pBufferInfo()} to the buffer segment described by {@code uniformBuffer}</li>
	 * </ul>
	 */
	public void writeUniformBuffer(long vkDescriptorSet, int binding, VkbBuffer uniformBuffer) {
		writeBuffer(vkDescriptorSet, binding, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, uniformBuffer);
	}

	/**
	 * Modifies the next {@link VkWriteDescriptorSet}. Sets:
	 * <ul>
	 *     <li>{@link VkWriteDescriptorSet#sType()} to the default structure type</li>
	 *     <li>{@link VkWriteDescriptorSet#dstSet()} to {@code vkDescriptorSet}</li>
	 *     <li>{@link VkWriteDescriptorSet#dstBinding()} to {@code binding}</li>
	 *     <li>{@link VkWriteDescriptorSet#descriptorCount()} to 1</li>
	 *     <li>{@link VkWriteDescriptorSet#descriptorType()} to {@link VK10#VK_DESCRIPTOR_TYPE_SAMPLER}</li>
	 *     <li>{@link VkWriteDescriptorSet#pImageInfo()} to ({@code vkSampler}, 0, 0)</li>
	 * </ul>
	 */
	public void writeSampler(long vkDescriptorSet, int binding, long vkSampler) {
		if (!imageWrites.hasRemaining()) flush();
		write(vkDescriptorSet, binding, VK_DESCRIPTOR_TYPE_SAMPLER);

		var imageInfo = imageWrites.get();
		imageInfo.set(vkSampler, VK_NULL_HANDLE, VK_IMAGE_LAYOUT_UNDEFINED);

		descriptorWrites.get(descriptorWrites.position() - 1).pImageInfo(VkDescriptorImageInfo.create(imageInfo.address(), 1));
	}

	/**
	 * Modifies the next {@link VkWriteDescriptorSet}. Sets:
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
	public void writeImage(long vkDescriptorSet, int binding, long vkImageView, long vkSampler) {
		if (!imageWrites.hasRemaining()) flush();
		int descriptorType = vkSampler == VK_NULL_HANDLE ? VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE : VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
		write(vkDescriptorSet, binding, descriptorType);

		var imageInfo = imageWrites.get();
		imageInfo.set(vkSampler, vkImageView, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

		descriptorWrites.get(descriptorWrites.position() - 1).pImageInfo(VkDescriptorImageInfo.create(imageInfo.address(), 1));
	}

	private void flush() {
		descriptorWrites.flip();
		if (descriptorWrites.limit() > 0) {
			vkUpdateDescriptorSets(instance.vkDevice(), descriptorWrites, null);
		}
		descriptorWrites.limit(descriptorWrites.capacity());
		bufferWrites.position(0);
		imageWrites.position(0);
	}

	/**
	 * Updates all descriptors that haven't been updated yet, and frees the used memory (if it wasn't allocated on a
	 * stack).
	 */
	public void finish() {
		flush();
		if (!usedStack) {
			descriptorWrites.free();
			bufferWrites.free();
			imageWrites.free();
		}
	}
}
