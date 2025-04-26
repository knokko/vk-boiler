package com.github.knokko.boiler.buffers;

/**
 * Represents a range/part of a <i>VkBuffer</i>
 * @param buffer The buffer
 * @param offset The offset into the buffer, in bytes
 * @param size The size of the range, in bytes
 */
public record VkbBufferRange(VkbBuffer buffer, long offset, long size) {

	/**
	 * Creates and returns a child/sub range of this buffer range
	 * @param childOffset The offset of the child range, relative to this buffer range, in bytes
	 * @param childSize The size of the child range, in bytes
	 */
	public VkbBufferRange childRange(long childOffset, long childSize) {
		return new VkbBufferRange(buffer, offset + childOffset, childSize);
	}
}
