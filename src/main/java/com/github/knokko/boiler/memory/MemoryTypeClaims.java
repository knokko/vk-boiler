package com.github.knokko.boiler.memory;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.memory.callbacks.CallbackUserData;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaAllocationInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;

import java.util.*;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVmaSuccess;
import static com.github.knokko.boiler.utilities.BoilerMath.leastCommonMultiple;
import static com.github.knokko.boiler.utilities.BoilerMath.nextMultipleOf;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.util.vma.Vma.vmaBindImageMemory2;
import static org.lwjgl.vulkan.VK10.*;

class MemoryTypeClaims {

	final Collection<BufferUsageClaims> buffers = new ArrayList<>();
	final Collection<ImageClaim> images = new ArrayList<>();
	private boolean mapMemory;

	private long prepareAllocations() {
		long offset = 0L;
		for (BufferUsageClaims claim : buffers) {
			offset = nextMultipleOf(offset, claim.memoryAlignment);
			claim.memoryOffset = offset;
			offset += claim.memorySize;
			if (claim.shouldMapMemory) mapMemory = true;
		}
		for (ImageClaim claim : images) {
			offset = nextMultipleOf(offset, claim.alignment);
			claim.memoryOffset = offset;
			offset += claim.memorySize;
		}
		return offset;
	}

	void allocate(BoilerInstance instance, String name, boolean useVma, int memoryType, MemoryBlock block) {
		long size = prepareAllocations();
		long allocation;
		long hostAddress = 0L;

		try (var stack = stackPush()) {
			var pAllocation = stack.callocPointer(1);
			if (useVma) {
				var vmaRequirements = VkMemoryRequirements.calloc(stack);
				Set<Long> alignments = new HashSet<>();
				for (BufferUsageClaims buffer : buffers) alignments.add(buffer.memoryAlignment);
				for (ImageClaim image : images) alignments.add(image.alignment);
				vmaRequirements.size(size);
				vmaRequirements.alignment(leastCommonMultiple(alignments));
				vmaRequirements.memoryTypeBits(1 << memoryType);

				var ciVmaAllocation = VmaAllocationCreateInfo.calloc(stack);
				ciVmaAllocation.flags(mapMemory ? VMA_ALLOCATION_CREATE_MAPPED_BIT : 0);
				ciVmaAllocation.memoryTypeBits(vmaRequirements.memoryTypeBits());

				var allocationInfo = VmaAllocationInfo.calloc(stack);
				assertVmaSuccess(vmaAllocateMemory(
						instance.vmaAllocator(), vmaRequirements, ciVmaAllocation, pAllocation, allocationInfo
				), "AllocateMemory", name);
				allocation = pAllocation.get(0);
				block.vmaAllocations.add(allocation);
				if (mapMemory) hostAddress = allocationInfo.pMappedData();
			} else {
				var aiMemory = VkMemoryAllocateInfo.calloc(stack);
				aiMemory.sType$Default();
				aiMemory.allocationSize(size);
				aiMemory.memoryTypeIndex(memoryType);

				var pMemory = stack.callocLong(1);
				assertVkSuccess(vkAllocateMemory(
						instance.vkDevice(), aiMemory, CallbackUserData.MEMORY.put(stack, instance), pMemory
				), "AllocateMemory", name);

				allocation = pMemory.get(0);
				instance.debug.name(stack, allocation, VK_OBJECT_TYPE_DEVICE_MEMORY, name);
				block.vkAllocations.add(allocation);

				if (mapMemory) {
					var pHostAddress = stack.callocPointer(1);
					assertVkSuccess(vkMapMemory(
							instance.vkDevice(), allocation, 0, VK_WHOLE_SIZE, 0, pHostAddress
					), "MapMemory", name);
					hostAddress = pHostAddress.get(0);
				}
			}
		}

		for (BufferUsageClaims buffer : buffers) {
			long vkBuffer = buffer.claims.get(0).buffer().vkBuffer;
			block.vkBuffers.add(vkBuffer);
			if (useVma) {
				assertVmaSuccess(vmaBindBufferMemory2(
						instance.vmaAllocator(), allocation, buffer.memoryOffset, vkBuffer, NULL
				), "BindBufferMemory2", name + ": buffer");
			} else {
				assertVkSuccess(vkBindBufferMemory(
						instance.vkDevice(), vkBuffer, allocation, buffer.memoryOffset
				), "BindBufferMemory", name + ": buffer");
			}
			if (buffer.shouldMapMemory) buffer.setHostAddress(hostAddress);
		}

		for (ImageClaim image : images) {
			block.vkImages.add(image.image.vkImage);
			if (useVma) {
				assertVmaSuccess(vmaBindImageMemory2(
						instance.vmaAllocator(), allocation, image.memoryOffset, image.image.vkImage, NULL
				), "BindImageMemory2", name + ": " + image);
			} else {
				assertVkSuccess(vkBindImageMemory(
						instance.vkDevice(), image.image.vkImage, allocation, image.memoryOffset
				), "BindImageMemory", name + ": " + image);
			}

			if (image.builder.shouldCreateView) {
				image.image.vkImageView = image.builder.createView(instance, image.image.vkImage);
				block.vkImageViews.add(image.image.vkImageView);
			}
		}
	}
}
