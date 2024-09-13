package com.github.knokko.boiler.buffers;

import com.github.knokko.boiler.BoilerInstance;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaAllocationInfo;
import org.lwjgl.vulkan.VkBufferCreateInfo;

import java.awt.*;
import java.awt.image.BufferedImage;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVmaSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memByteBuffer;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.util.vma.Vma.VMA_ALLOCATION_CREATE_MAPPED_BIT;
import static org.lwjgl.vulkan.VK10.*;

public class BoilerBuffers {

	private final BoilerInstance instance;

	/**
	 * This constructor is meant for internal use only. You should use <i>boilerInstance.buffers</i> instead.
	 */
	public BoilerBuffers(BoilerInstance instance) {
		this.instance = instance;
	}

	/**
	 * Creates a <i>DeviceVkbBuffer</i> with the given size and usage. Unlike the <i>create(...)</i> method, this
	 * method will use the raw <i>vkCreateBuffer</i> function to create the buffer, and no memory will be bound or
	 * allocated to the buffer.
	 * @param size The size of the <i>VkBuffer</i>, in bytes
	 * @param usage The <i>VK_BUFFER_USAGE_FLAG_BITS</i> of the <i>VkBuffer</i>
	 * @param name The name that should be given to the buffer, when validation is enabled
	 * @return A <i>DeviceVkbBuffer</i> that wraps the created <i>VkBuffer</i>
	 */
	public DeviceVkbBuffer createRaw(long size, int usage, String name) {
		try (var stack = stackPush()) {
			var ciBuffer = VkBufferCreateInfo.calloc(stack);
			ciBuffer.sType$Default();
			ciBuffer.flags(0);
			ciBuffer.size(size);
			ciBuffer.usage(usage);
			ciBuffer.sharingMode(VK_SHARING_MODE_EXCLUSIVE);

			var pBuffer = stack.callocLong(1);
			assertVkSuccess(vkCreateBuffer(
					instance.vkDevice(), ciBuffer, null, pBuffer
			), "CreateBuffer", name);
			instance.debug.name(stack, pBuffer.get(0), VK_OBJECT_TYPE_BUFFER, name);
			return new DeviceVkbBuffer(pBuffer.get(0), VK_NULL_HANDLE, size);
		}
	}

	/**
	 * Creates a <i>DeviceVkbBuffer</i> with the given size and usage. This method will use <i>vmaCreateBuffer</i> to
	 * create the buffer.
	 * @param size The size of the <i>VkBuffer</i>, in bytes
	 * @param usage The <i>VK_BUFFER_USAGE_FLAG_BITS</i> of the <i>VkBuffer</i>
	 * @param name The name that should be given to the buffer, when validation is enabled
	 * @return A <i>DeviceVkbBuffer</i> that wraps the created <i>VkBuffer</i>
	 */
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

	/**
	 * Creates a <i>VkBuffer</i> with the given size and usage in host-visible memory, and maps its memory. This
	 * method will use <i>vmaCreateBuffer</i> to create the buffer.
	 * @param size The size of the buffer, in bytes
	 * @param usage The <i>VK_BUFFER_USAGE_FLAG_BITS</i> of the <i>VkBuffer</i>
	 * @param name The name that should be given to the buffer, when validation is enabled
	 * @return A <i>MappedVkbBuffer</i> that wraps the created <i>VkBuffer</i>
	 */
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

	/**
	 * Stores the given image at the given offset in the given buffer, using RGBA8 encoding. This is intended to be
	 * used before <i>vkCmdCopyBufferToImage</i>.
	 * @param mappedBuffer The buffer in which the image data should be stored
	 * @param image The image whose pixels should be stored in the buffer
	 * @param offset The offset into the buffer where the first pixel should be stored (usually 0)
	 */
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

	/**
	 * Decodes a <i>BufferedImage</i> with the given size from the pixel data in the given buffer at the given offset.
	 * This method expects the image data to be in RGBA8 format.
	 * @param mappedBuffer The buffer that contains the image data to be decoded
	 * @param offset The offset into the buffer where the first pixel is stored
	 * @param width The width of the image
	 * @param height The height of the image
	 * @return The decoded image
	 */
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
