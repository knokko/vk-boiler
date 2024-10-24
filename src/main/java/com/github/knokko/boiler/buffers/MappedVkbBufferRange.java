package com.github.knokko.boiler.buffers;

import java.nio.*;

import static org.lwjgl.system.MemoryUtil.*;

/**
 * Represents a range/section of a <i>MappedVkbBuffer</i>
 * @param buffer The mapped buffer
 * @param offset The offset into the mapped buffer, in bytes
 * @param size The size of this range, in bytes
 */
public record MappedVkbBufferRange(MappedVkbBuffer buffer, long offset, long size) {

	/**
	 * @return The start host address of this buffer range
	 */
	public long hostAddress() {
		return buffer.hostAddress() + offset;
	}

	/**
	 * @return The size of this range <b>in bytes</b>, but represented as integer
	 * @throws UnsupportedOperationException When the size is larger than <i>Integer.MAX_VALUE</i>
	 */
	public int intSize() {
		if (size > Integer.MAX_VALUE) throw new UnsupportedOperationException("Size (" + size + ") is too large");
		return (int) size;
	}

	/**
	 * @return The corresponding <i>VkbBufferRange</i>
	 */
	public VkbBufferRange range() {
		return new VkbBufferRange(buffer, offset, size);
	}

	/**
	 * @return A direct byte buffer that starts at the start of this range, and has the same capacity
	 */
	public ByteBuffer byteBuffer() {
		return memByteBuffer(hostAddress(), intSize());
	}

	/**
	 * @return A direct short buffer that starts at the start of this range, with a capacity of <i>size / Short.BYTES</i>
	 */
	public ShortBuffer shortBuffer() {
		return memShortBuffer(hostAddress(), intSize() / Short.BYTES);
	}

	/**
	 * @return A direct int buffer that starts at the start of this range, with a capacity of <i>size / Int.BYTES</i>
	 */
	public IntBuffer intBuffer() {
		return memIntBuffer(hostAddress(), intSize() / Integer.BYTES);
	}

	/**
	 * @return A direct float buffer that starts at the start of this range, with a capacity of <i>size / Float.BYTES</i>
	 */
	public FloatBuffer floatBuffer() {
		return memFloatBuffer(hostAddress(), intSize() / Float.BYTES);
	}

	/**
	 * @return A direct long buffer that starts at the start of this range, with a capacity of <i>size / Long.BYTES</i>
	 */
	public LongBuffer longBuffer() {
		return memLongBuffer(hostAddress(), intSize() / Long.BYTES);
	}

	/**
	 * @return A direct double buffer that starts at the start of this range, with a capacity of <i>size / Double.BYTES</i>
	 */
	public DoubleBuffer doubleBuffer() {
		return memDoubleBuffer(hostAddress(), intSize() / Double.BYTES);
	}
}
