package com.github.knokko.boiler.buffers;

import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;

/**
 * A {@link VkbBuffer} represents a segment of a <b>VkBuffer</b>. Every initialized instance claims bytes
 * {@code offset} to {@code offset + size} of {@code vkBuffer}.
 */
public class VkbBuffer {

	/**
	 * The <b>VkBuffer</b>, or <b>VK_NULL_HANDLE</b> when not yet initialized
	 */
	public long vkBuffer;

	/**
	 * The offset (in bytes) of this {@link VkbBuffer} into the <b>VkBuffer</b>. When this is not zero, the first byte
	 * of the <b>VkBuffer</b> is claimed by another {@link VkbBuffer}.
	 */
	public long offset;

	/**
	 * The size of this {@link VkbBuffer}, in bytes. Note that {@code vkBuffer} can be larger.
	 */
	public final long size;

	public VkbBuffer(long vkBuffer, long offset, long size) {
		this.vkBuffer = vkBuffer;
		this.offset = offset;
		this.size = size;
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof VkbBuffer) {
			VkbBuffer buffer = (VkbBuffer) other;
			return vkBuffer == buffer.vkBuffer && offset == buffer.offset && size == buffer.size;
		} else return false;
	}

	@Override
	public int hashCode() {
		return (int) vkBuffer + 13 * (int) offset - 31 * (int) size;
	}

	/**
	 * Creates an uninitialized {@link VkbBuffer} that will have the given size (in bytes)
	 */
	public VkbBuffer(long size) {
		this(VK_NULL_HANDLE, -1, size);
	}

	protected void validateChildRange(long childOffset, long childSize) {
		if (childOffset + childSize > size) throw new IllegalArgumentException(
				"Child (offset=" + childOffset + ", size=" + childSize + ") out of range for parent (offset="
						+ offset + ", size=" + size + ")"
		);
	}

	public VkbBuffer child(long childOffset, long childSize) {
		validateChildRange(childOffset, childSize);
		return new VkbBuffer(vkBuffer, offset + childOffset, childSize);
	}
}
