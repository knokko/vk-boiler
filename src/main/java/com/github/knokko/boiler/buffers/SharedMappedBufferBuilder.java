package com.github.knokko.boiler.buffers;

import com.github.knokko.boiler.BoilerInstance;

import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;

/**
 * A helper class that can be used to easily create 1 <i>MappedVkbBuffer</i> that is shared by multiple
 * non-overlapping <i>MappedVkbBufferRange</i>s. You can use this to, for instance, create multiple staging buffer
 * sections that all use a different region of the same (staging) <i>VkBuffer</i>. You can use this class by:
 * <ol>
 *     <li>Create an instance using the constructor</li>
 *     <li>Call the <i>add()</i> method for each section you want, and keep the returned <i>Supplier</i>s</li>
 *     <li>Call the <i>build()</i> method</li>
 *     <li>Call the <i>get()</i> methods of the returned <i>Supplier</i>s to get the ranges</li>
 * </ol>
 */
public class SharedMappedBufferBuilder extends SharedBufferBuilder<MappedVkbBuffer, MappedVkbBufferRange> {

	/**
	 * Creates a new {@link SharedMappedBufferBuilder}
	 */
	public SharedMappedBufferBuilder(BoilerInstance instance) {
		super(instance);
	}

	@Override
	protected MappedVkbBuffer buildBuffer(long size, int usage, String name) {
		return instance.buffers.createMapped(size, usage, name);
	}

	@Override
	protected MappedVkbBufferRange createRange(MappedVkbBuffer buffer, long offset, long size) {
		return new MappedVkbBufferRange(buffer, offset, size);
	}

	/**
	 * This method is needed by {@link com.github.knokko.boiler.memory.SharedMemoryBuilder} for internal use. I would
	 * not recommend to call this yourself.
	 */
	public long buildRaw(int usage, String name) {
		return instance.buffers.createRaw(totalSize, usage, name).vkBuffer();
	}

	/**
	 * This method is needed by {@link com.github.knokko.boiler.memory.SharedMemoryBuilder} for internal use. I would
	 * not recommend to call this yourself.
	 */
	public void setRaw(long vkBuffer, long hostAddress) {
		this.buffer = new MappedVkbBuffer(vkBuffer, VK_NULL_HANDLE, totalSize, hostAddress);
	}
}
