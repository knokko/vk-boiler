package com.github.knokko.boiler.buffers;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.*;

import static java.lang.Math.toIntExact;
import static org.lwjgl.system.MemoryUtil.*;

public class MappedVkbBuffer extends VkbBuffer {

	public long hostAddress;

	public MappedVkbBuffer(long vkBuffer, long offset, long size, long hostAddress) {
		super(vkBuffer, offset, size);
		this.hostAddress = hostAddress;
	}

	public MappedVkbBuffer(long size) {
		super(size);
	}

	@Override
	public MappedVkbBuffer child(long childOffset, long childSize) {
		validateChildRange(childOffset, childSize);
		return new MappedVkbBuffer(vkBuffer, offset + childOffset, childSize, hostAddress + childOffset);
	}

	public ByteBuffer byteBuffer() {
		return memByteBuffer(hostAddress, toIntExact(size));
	}

	public ShortBuffer shortBuffer() {
		return memShortBuffer(hostAddress, toIntExact(size / Short.BYTES));
	}

	public CharBuffer charBuffer() {
		return memCharBuffer(hostAddress, toIntExact(size / Character.BYTES));
	}

	public IntBuffer intBuffer() {
		return memIntBuffer(hostAddress, toIntExact(size / Integer.BYTES));
	}

	public FloatBuffer floatBuffer() {
		return memFloatBuffer(hostAddress, toIntExact(size / Float.BYTES));
	}

	public LongBuffer longBuffer() {
		return memLongBuffer(hostAddress, toIntExact(size / Long.BYTES));
	}

	public DoubleBuffer doubleBuffer() {
		return memDoubleBuffer(hostAddress, toIntExact(size / Double.BYTES));
	}

	/**
	 * Stores the given image in this buffer, using RGBA8 encoding. This is intended to be
	 * used before <i>vkCmdCopyBufferToImage</i>.
	 * @param image The image whose pixels should be stored in the buffer
	 */
	public void encodeBufferedImage(BufferedImage image) {
		long expectedSize = 4L * image.getWidth() * image.getHeight();
		if (size != expectedSize) {
			throw new IllegalArgumentException("Expected destination size to be " + expectedSize + ", but got " + size);
		}

		var byteBuffer = byteBuffer();
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				var pixel = new Color(image.getRGB(x, y), true);
				byteBuffer.put((byte) pixel.getRed());
				byteBuffer.put((byte) pixel.getGreen());
				byteBuffer.put((byte) pixel.getBlue());
				byteBuffer.put((byte) pixel.getAlpha());
			}
		}
	}

	/**
	 * Decodes a <i>BufferedImage</i> with the given size from this buffer.
	 * This method expects the image data to be in RGBA8 format.
	 * @param width The width of the image
	 * @param height The height of the image
	 * @return The decoded image
	 */
	public BufferedImage decodeBufferedImage(int width, int height) {
		long expectedSize = 4L * width * height;
		if (size != expectedSize) {
			throw new IllegalArgumentException("Expected source size to be " + expectedSize + ", but got " + size);
		}

		var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		var byteBuffer = byteBuffer();
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				var pixel = new Color(
						byteBuffer.get() & 0xFF,
						byteBuffer.get() & 0xFf,
						byteBuffer.get() & 0xFF,
						byteBuffer.get() & 0xFF
				);
				image.setRGB(x, y, pixel.getRGB());
			}
		}

		return image;
	}
}
