package com.github.knokko.boiler.buffer;

import com.github.knokko.boiler.BoilerInstance;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaAllocationInfo;
import org.lwjgl.vulkan.VkBufferCreateInfo;

import java.awt.*;
import java.awt.image.BufferedImage;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVmaSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memByteBuffer;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.util.vma.Vma.VMA_ALLOCATION_CREATE_MAPPED_BIT;
import static org.lwjgl.vulkan.VK10.*;

public class BoilerBuffers {

	private final BoilerInstance instance;

	public BoilerBuffers(BoilerInstance instance) {
		this.instance = instance;
	}

	public DeviceVkbBuffer create(long size, int usage, String name) {
		try (var stack = stackPush()) {
			var ciBuffer = VkBufferCreateInfo.calloc(stack);
			ciBuffer.sType$Default();
			ciBuffer.flags(0);
			ciBuffer.size(size);
			ciBuffer.usage(usage);
			ciBuffer.sharingMode(VK_SHARING_MODE_EXCLUSIVE);

			var ciAllocation = VmaAllocationCreateInfo.calloc(stack);
			ciAllocation.usage(VMA_MEMORY_USAGE_AUTO);

			var pBuffer = stack.callocLong(1);
			var pAllocation = stack.callocPointer(1);

			assertVmaSuccess(vmaCreateBuffer(
					instance.vmaAllocator(), ciBuffer, ciAllocation, pBuffer, pAllocation, null
			), "CreateBuffer", name);
			instance.debug.name(stack, pBuffer.get(0), VK_OBJECT_TYPE_BUFFER, name);
			return new DeviceVkbBuffer(pBuffer.get(0), pAllocation.get(0), size);
		}
	}

	public MappedVkbBuffer createMapped(long size, int usage, String name) {
		try (var stack = stackPush()) {
			var ciBuffer = VkBufferCreateInfo.calloc(stack);
			ciBuffer.sType$Default();
			ciBuffer.flags(0);
			ciBuffer.size(size);
			ciBuffer.usage(usage);
			ciBuffer.sharingMode(VK_SHARING_MODE_EXCLUSIVE);

			var ciAllocation = VmaAllocationCreateInfo.calloc(stack);
			ciAllocation.flags(
					VMA_ALLOCATION_CREATE_HOST_ACCESS_RANDOM_BIT | VMA_ALLOCATION_CREATE_MAPPED_BIT
			);
			ciAllocation.usage(VMA_MEMORY_USAGE_AUTO);

			var pBuffer = stack.callocLong(1);
			var pAllocation = stack.callocPointer(1);
			var pInfo = VmaAllocationInfo.calloc(stack);

			assertVmaSuccess(vmaCreateBuffer(
					instance.vmaAllocator(), ciBuffer, ciAllocation, pBuffer, pAllocation, pInfo
			), "CreateBuffer", name);
			instance.debug.name(stack, pBuffer.get(0), VK_OBJECT_TYPE_BUFFER, name);
			return new MappedVkbBuffer(pBuffer.get(0), pAllocation.get(0), size, pInfo.pMappedData());
		}
	}

	public void encodeBufferedImageRGBA(MappedVkbBuffer mappedBuffer, BufferedImage image, long offset) {
		int requiredSize = 4 * image.getWidth() * image.getHeight();
		if (offset + requiredSize > mappedBuffer.size()) throw new IllegalArgumentException("Buffer is too small");

		var byteBuffer = memByteBuffer(mappedBuffer.hostAddress() + offset, requiredSize);
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

	public BufferedImage decodeBufferedImageRGBA(MappedVkbBuffer mappedBuffer, long offset, int width, int height) {
		int requiredSize = 4 * width * height;
		if (offset + requiredSize > mappedBuffer.size()) throw new IllegalArgumentException("Buffer is too small");

		var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		var byteBuffer = memByteBuffer(mappedBuffer.hostAddress() + offset, requiredSize);
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
