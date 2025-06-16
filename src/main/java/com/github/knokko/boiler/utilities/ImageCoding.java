package com.github.knokko.boiler.utilities;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

public class ImageCoding {

	/**
	 * Stores {@code iamge} in {@code byteBuffer}, using RGBA8 encoding.
	 */
	public static void encodeBufferedImage(ByteBuffer byteBuffer, BufferedImage image) {
		long expectedSize = 4L * image.getWidth() * image.getHeight();
		if (byteBuffer.remaining() != expectedSize) {
			throw new IllegalArgumentException("Expected destination size to be " + expectedSize + ", but got " + byteBuffer.remaining());
		}

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
	 * Decodes a <i>BufferedImage</i> with the given size from {@code byteBuffer}.
	 * This method expects the image data to be in RGBA8 format.
	 * @param width The width of the image
	 * @param height The height of the image
	 * @return The decoded image
	 */
	public static BufferedImage decodeBufferedImage(ByteBuffer byteBuffer, int width, int height) {
		long expectedSize = 4L * width * height;
		if (byteBuffer.remaining() != expectedSize) {
			throw new IllegalArgumentException("Expected source size to be " + expectedSize + ", but got " + byteBuffer.remaining());
		}

		var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
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
