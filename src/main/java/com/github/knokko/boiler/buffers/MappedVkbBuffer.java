package com.github.knokko.boiler.buffers;

import com.github.knokko.boiler.utilities.ImageCoding;

import java.awt.image.BufferedImage;
import java.nio.*;

import static java.lang.Math.toIntExact;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * A host-visible {@link VkbBuffer} whose memory has been mapped. {@link com.github.knokko.boiler.memory.MemoryCombiner}
 * is the recommended method to create (Mapped)VkbBuffer's.
 */
public class MappedVkbBuffer extends VkbBuffer {

	/**
	 * The memory address where the first byte of this MappedVkbBuffer is mapped
	 */
	public long hostAddress;

	public MappedVkbBuffer(long vkBuffer, long offset, long size, long hostAddress) {
		super(vkBuffer, offset, size);
		this.hostAddress = hostAddress;
	}

	/**
	 * Creates a MappedVkbBuffer with the given size. All other fields will be uninitialized
	 */
	public MappedVkbBuffer(long size) {
		super(size);
	}

	@Override
	public MappedVkbBuffer child(long childOffset, long childSize) {
		validateChildRange(childOffset, childSize);
		return new MappedVkbBuffer(vkBuffer, offset + childOffset, childSize, hostAddress + childOffset);
	}

	/**
	 * @return a ByteBuffer view of the memory that is mapped by this buffer
	 */
	public ByteBuffer byteBuffer() {
		return memByteBuffer(hostAddress, toIntExact(size));
	}

	/**
	 * @return a ShortBuffer view of the memory that is mapped by this buffer
	 */
	public ShortBuffer shortBuffer() {
		return memShortBuffer(hostAddress, toIntExact(size / Short.BYTES));
	}

	/**
	 * @return a CharBuffer view of the memory that is mapped by this buffer
	 */
	public CharBuffer charBuffer() {
		return memCharBuffer(hostAddress, toIntExact(size / Character.BYTES));
	}

	/**
	 * @return an IntBuffer view of the memory that is mapped by this buffer
	 */
	public IntBuffer intBuffer() {
		return memIntBuffer(hostAddress, toIntExact(size / Integer.BYTES));
	}

	/**
	 * @return a FloatBuffer view of the memory that is mapped by this buffer
	 */
	public FloatBuffer floatBuffer() {
		return memFloatBuffer(hostAddress, toIntExact(size / Float.BYTES));
	}

	/**
	 * @return a LongBuffer view of the memory that is mapped by this buffer
	 */
	public LongBuffer longBuffer() {
		return memLongBuffer(hostAddress, toIntExact(size / Long.BYTES));
	}

	/**
	 * @return a DoubleBuffer view of the memory that is mapped by this buffer
	 */
	public DoubleBuffer doubleBuffer() {
		return memDoubleBuffer(hostAddress, toIntExact(size / Double.BYTES));
	}

	/**
	 * Stores the given image in this buffer, using RGBA8 encoding. This is intended to be
	 * used before <i>vkCmdCopyBufferToImage</i>.
	 * @param image The image whose pixels should be stored in the buffer
	 */
	public void encodeBufferedImage(BufferedImage image) {
		ImageCoding.encodeBufferedImage(byteBuffer(), image);
	}

	/**
	 * Decodes a <i>BufferedImage</i> with the given size from this buffer.
	 * This method expects the image data to be in RGBA8 format.
	 * @param width The width of the image
	 * @param height The height of the image
	 * @return The decoded image
	 */
	public BufferedImage decodeBufferedImage(int width, int height) {
		return ImageCoding.decodeBufferedImage(byteBuffer(), width, height);
	}
}
