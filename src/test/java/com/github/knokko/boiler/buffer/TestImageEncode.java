package com.github.knokko.boiler.buffer;

import com.github.knokko.boiler.builder.BoilerBuilder;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.system.MemoryUtil.memGetByte;
import static org.lwjgl.system.MemoryUtil.memPutByte;
import static org.lwjgl.vulkan.VK10.VK_API_VERSION_1_0;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
import static org.lwjgl.vulkan.VK11.VK_API_VERSION_1_1;
import static org.lwjgl.vulkan.VK12.VK_API_VERSION_1_2;

public class TestImageEncode {

	@Test
	public void testEncodeBufferedImageRGBA() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_0, "TestEncodeBufferedImageRGBA", 1
		).validation().forbidValidationErrors().build();

		var buffer = instance.buffers.createMapped(10, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, "DestBuffer");
		var image = new BufferedImage(1, 2, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(0, 0, Color.RED.getRGB());
		image.setRGB(0, 1, new Color(50, 100, 150, 200).getRGB());

		instance.buffers.encodeBufferedImageRGBA(buffer, image, 1);
		assertEquals((byte) 255, memGetByte(buffer.hostAddress() + 1));
		assertEquals((byte) 0, memGetByte(buffer.hostAddress() + 2));
		assertEquals((byte) 0, memGetByte(buffer.hostAddress() + 3));
		assertEquals((byte) 255, memGetByte(buffer.hostAddress() + 4));

		assertEquals((byte) 50, memGetByte(buffer.hostAddress() + 5));
		assertEquals((byte) 100, memGetByte(buffer.hostAddress() + 6));
		assertEquals((byte) 150, memGetByte(buffer.hostAddress() + 7));
		assertEquals((byte) 200, memGetByte(buffer.hostAddress() + 8));

		buffer.destroy(instance);
		instance.destroyInitialObjects();
	}

	@Test
	public void testDecodeBufferedImageRGBA() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_1, "TestDecodeBufferedImageRGBA", 1
		).validation().forbidValidationErrors().build();

		var buffer = instance.buffers.createMapped(10, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, "DestBuffer");

		memPutByte(buffer.hostAddress() + 1, (byte) 255);
		memPutByte(buffer.hostAddress() + 2, (byte) 0);
		memPutByte(buffer.hostAddress() + 3, (byte) 0);
		memPutByte(buffer.hostAddress() + 4, (byte) 255);

		memPutByte(buffer.hostAddress() + 5, (byte) 50);
		memPutByte(buffer.hostAddress() + 6, (byte) 100);
		memPutByte(buffer.hostAddress() + 7, (byte) 150);
		memPutByte(buffer.hostAddress() + 8, (byte) 200);

		var image = instance.buffers.decodeBufferedImageRGBA(buffer, 1, 1, 2);
		assertEquals(Color.RED, new Color(image.getRGB(0, 0), true));
		assertEquals(new Color(50, 100, 150, 200), new Color(image.getRGB(0, 1), true));

		buffer.destroy(instance);
		instance.destroyInitialObjects();
	}

	@Test
	public void testBufferedImageCodingWithoutAlpha() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_2, "TestBufferedImageCodingWithoutAlpha", 1
		).validation().forbidValidationErrors().build();

		var buffer = instance.buffers.createMapped(10, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, "DestBuffer");
		var image = new BufferedImage(1, 2, BufferedImage.TYPE_INT_RGB);
		image.setRGB(0, 0, Color.RED.getRGB());
		image.setRGB(0, 1, new Color(50, 100, 150).getRGB());

		instance.buffers.encodeBufferedImageRGBA(buffer, image, 1);
		assertEquals((byte) 255, memGetByte(buffer.hostAddress() + 1));
		assertEquals((byte) 0, memGetByte(buffer.hostAddress() + 2));
		assertEquals((byte) 0, memGetByte(buffer.hostAddress() + 3));
		assertEquals((byte) 255, memGetByte(buffer.hostAddress() + 4));

		assertEquals((byte) 50, memGetByte(buffer.hostAddress() + 5));
		assertEquals((byte) 100, memGetByte(buffer.hostAddress() + 6));
		assertEquals((byte) 150, memGetByte(buffer.hostAddress() + 7));
		assertEquals((byte) 255, memGetByte(buffer.hostAddress() + 8));

		var copied = instance.buffers.decodeBufferedImageRGBA(buffer, 1, image.getWidth(), image.getHeight());
		assertEquals(image.getRGB(0, 0), copied.getRGB(0, 0));
		assertEquals(image.getRGB(0, 1), copied.getRGB(0, 1));
		assertEquals(255, new Color(copied.getRGB(0, 0), true).getAlpha());

		buffer.destroy(instance);
		instance.destroyInitialObjects();
	}
}
