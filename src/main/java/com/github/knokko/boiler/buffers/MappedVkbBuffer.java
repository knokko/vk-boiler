package com.github.knokko.boiler.buffers;

/**
 * Represents a <i>VkBuffer</i> with the given size and optional <i>VmaAllocation</i>. The buffer must be stored in
 * host-visible memory, and must be mapped at <i>hostAddress</i> during its lifetime.
 */
public record MappedVkbBuffer(long vkBuffer, long vmaAllocation, long size, long hostAddress) implements VkbBuffer {

	/**
	 * @return a <i>MappedVkbBufferRange</i> that covers this whole buffer
	 */
	public MappedVkbBufferRange fullMappedRange() {
		return new MappedVkbBufferRange(this, 0L, size);
	}

	/**
	 * @param offset The offset, in bytes
	 * @param rangeSize The size of the range, in bytes
	 * @return a <i>MappedVkbBufferRange</i> that covers the bytes [offset, offset + rangeSize> from this buffer
	 */
	public MappedVkbBufferRange mappedRange(long offset, long rangeSize) {
		if (offset + rangeSize > size) throw new IllegalArgumentException(offset + " + " + rangeSize + " > " + size);
		return new MappedVkbBufferRange(this, offset, rangeSize);
	}
}
