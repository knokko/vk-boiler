package com.github.knokko.boiler.memory;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.memory.callbacks.CallbackUserData;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaAllocationInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryPriorityAllocateInfoEXT;
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
import static org.lwjgl.vulkan.EXTPageableDeviceLocalMemory.vkSetDeviceMemoryPriorityEXT;
import static org.lwjgl.vulkan.VK10.*;

class MemoryTypeClaims {

	final Collection<BufferUsageClaims> buffers = new ArrayList<>();
	final Collection<ImageClaim> images = new ArrayList<>();
	private boolean[] mapMemory;

	List<Long> prepareAllocations(long bufferImageGranularity, long maxSize) {
		var allocationSizes = new ArrayList<Long>();
		long offset = 0L;

		for (BufferUsageClaims claim : buffers) {
			for (int groupIndex = 0; groupIndex < claim.groupedClaims.size(); groupIndex++) {
				var group = claim.groupedClaims.get(groupIndex);
				if (group.memorySize > maxSize) {
					throw new IllegalArgumentException(
							"Buffer size (" + group.memorySize + ") exceeds maxMemoryAllocationSize (" + maxSize + ")"
					);
				}

				long oldSize = offset;
				offset = nextMultipleOf(offset, group.memoryAlignment);
				group.memoryOffset = offset;
				offset += group.memorySize;

				long newSize = offset;
				if (newSize > maxSize) {
					group.memoryOffset = 0L;
					offset = group.memorySize;
					allocationSizes.add(oldSize);
				}

				group.allocationIndex = allocationSizes.size();
			}
		}

		int numLinearImages = 0;
		for (ImageClaim claim : images) {
			if (claim.builder.tiling != VK_IMAGE_TILING_LINEAR) continue;
			if (claim.memorySize > maxSize) {
				throw new IllegalArgumentException(
						"Image size " + claim.builder.width + "x" + claim.builder.height +
								" (" + claim.memorySize + " bytes) exceeds maxMemoryAllocationSize (" + maxSize + ")"
				);
			}

			long oldSize = offset;
			offset = nextMultipleOf(offset, claim.alignment);
			claim.memoryOffset = offset;
			offset += claim.memorySize;

			long newSize = offset;
			if (newSize > maxSize) {
				claim.memoryOffset = 0L;
				offset = claim.memorySize;
				allocationSizes.add(oldSize);
			}

			claim.allocationIndex = allocationSizes.size();
		}

		if (numLinearImages != images.size()) {
			long oldSize = offset;
			long bufferEndPage = offset & -bufferImageGranularity;
			offset = bufferEndPage + bufferImageGranularity;

			for (ImageClaim claim : images) {
				if (claim.builder.tiling == VK_IMAGE_TILING_LINEAR) continue;
				if (claim.memorySize > maxSize) {
					throw new IllegalArgumentException(
							"Image size " + claim.builder.width + "x" + claim.builder.height +
									" (" + claim.memorySize + " bytes) exceeds maxMemoryAllocationSize (" + maxSize + ")"
					);
				}

				offset = nextMultipleOf(offset, claim.alignment);
				claim.memoryOffset = offset;
				offset += claim.memorySize;

				long newSize = offset;
				if (newSize > maxSize) {
					claim.memoryOffset = 0L;
					offset = claim.memorySize;
					allocationSizes.add(oldSize);
				}

				claim.allocationIndex = allocationSizes.size();
				oldSize = offset;
			}
		}

		if (offset > 0L) allocationSizes.add(offset);
		this.mapMemory = new boolean[allocationSizes.size()];
		for (var bufferClaim : buffers) {
			for (var grouped : bufferClaim.groupedClaims) {
				if (grouped.shouldMapMemory) mapMemory[grouped.allocationIndex] = true;
			}
		}

		return allocationSizes;
	}

	void allocate(
			BoilerInstance instance, String name, boolean useVma, MemoryBlock old,
			int memoryType, int backupMemoryType, float priority, MemoryBlock block
	) {
		long maxAllocationSize = 123456789012345L;
		if (instance.maintenance3Properties != null) {
			maxAllocationSize = instance.maintenance3Properties.maxMemoryAllocationSize();
		}
		List<Long> sizes = prepareAllocations(
				instance.deviceProperties.limits().bufferImageGranularity(),
				maxAllocationSize
		);
		long[] allocations = new long[sizes.size()];
		long[] hostAddresses = new long[allocations.length];

		for (int allocationIndex = 0; allocationIndex < allocations.length; allocationIndex++) {
			boolean mapMemory = this.mapMemory[allocationIndex];
			int finalAllocationIndex = allocationIndex;
			long size = sizes.get(allocationIndex);
			var currentBufferGroups = buffers.stream().flatMap(
					buffer -> buffer.groupedClaims.stream()
			).filter(
					group -> group.allocationIndex == finalAllocationIndex
			).toList();
			var currentImages = images.stream().filter(
					image -> image.allocationIndex == finalAllocationIndex
			).toList();
			try (var stack = stackPush()) {
				var pAllocation = stack.callocPointer(1);
				if (useVma) {
					var vmaRequirements = VkMemoryRequirements.calloc(stack);
					Set<Long> alignments = new HashSet<>();
					for (var buffer : currentBufferGroups) alignments.add(buffer.memoryAlignment);
					for (ImageClaim image : currentImages) alignments.add(image.alignment);
					vmaRequirements.size(size);
					vmaRequirements.alignment(leastCommonMultiple(alignments));
					vmaRequirements.memoryTypeBits(1 << memoryType);

					var ciVmaAllocation = VmaAllocationCreateInfo.calloc(stack);
					ciVmaAllocation.flags(mapMemory ? VMA_ALLOCATION_CREATE_MAPPED_BIT : 0);
					ciVmaAllocation.memoryTypeBits(vmaRequirements.memoryTypeBits());

					var allocationInfo = VmaAllocationInfo.calloc(stack);
					int allocateResult = vmaAllocateMemory(
							instance.vmaAllocator(), vmaRequirements, ciVmaAllocation, pAllocation, allocationInfo
					);
					if (allocateResult == VK_ERROR_OUT_OF_DEVICE_MEMORY && memoryType != backupMemoryType) {
						memoryType = backupMemoryType;
						vmaRequirements.memoryTypeBits(1 << memoryType);
						ciVmaAllocation.memoryTypeBits(vmaRequirements.memoryTypeBits());
						allocateResult = vmaAllocateMemory(
								instance.vmaAllocator(), vmaRequirements, ciVmaAllocation, pAllocation, allocationInfo
						);
					}
					assertVmaSuccess(allocateResult, "AllocateMemory", size + " bytes for " + name);
					allocations[allocationIndex] = pAllocation.get(0);
					block.vmaAllocations.add(allocations[allocationIndex]);
					if (mapMemory) hostAddresses[allocationIndex] = allocationInfo.pMappedData();


				} else {
					var oldAllocation = old != null ? old.getAllocation(memoryType) : null;
					if (oldAllocation != null && !oldAllocation.wasRecycled) {
						boolean canRecycle = oldAllocation.size >= size &&
								(!mapMemory || oldAllocation.hostAddress != 0L) &&
								(oldAllocation.priority == priority || instance.extra.pageableMemory());
						if (canRecycle) oldAllocation.wasRecycled = true;
						else oldAllocation = null;
					}

					if (oldAllocation != null) {
						allocations[allocationIndex] = oldAllocation.vkAllocation;
						memoryType = oldAllocation.memoryType;
						size = oldAllocation.size;
						hostAddresses[allocationIndex] = oldAllocation.hostAddress;

						if (oldAllocation.priority != priority) {
							vkSetDeviceMemoryPriorityEXT(instance.vkDevice(), allocations[allocationIndex], priority);
						}
					} else {
						var aiMemory = VkMemoryAllocateInfo.calloc(stack);
						aiMemory.sType$Default();
						aiMemory.allocationSize(size);
						aiMemory.memoryTypeIndex(memoryType);

						if (instance.extra.memoryPriority()) {
							var aiPriority = VkMemoryPriorityAllocateInfoEXT.calloc(stack);
							aiPriority.sType$Default();
							aiPriority.priority(priority);

							aiMemory.pNext(aiPriority);
						}

						var pMemory = stack.callocLong(1);
						int allocationResult = VK_ERROR_OUT_OF_DEVICE_MEMORY;
						if (instance.memoryInfo.getCapacity(memoryType) >= size) {
							allocationResult = vkAllocateMemory(
									instance.vkDevice(), aiMemory, CallbackUserData.MEMORY.put(stack, instance), pMemory
							);
						}

						String context = size + " bytes for " + name;
						if (allocationResult == VK_ERROR_OUT_OF_DEVICE_MEMORY && backupMemoryType != memoryType) {
							aiMemory.memoryTypeIndex(backupMemoryType);
							memoryType = backupMemoryType;
							allocationResult = vkAllocateMemory(
									instance.vkDevice(), aiMemory, CallbackUserData.MEMORY.put(stack, instance), pMemory
							);
							context += " (retry)";
						}
						assertVkSuccess(allocationResult, "AllocateMemory", context);

						allocations[allocationIndex] = pMemory.get(0);
					}

					instance.debug.name(stack, allocations[allocationIndex], VK_OBJECT_TYPE_DEVICE_MEMORY, name);

					if (mapMemory && oldAllocation == null) {
						var pHostAddress = stack.callocPointer(1);
						assertVkSuccess(vkMapMemory(
								instance.vkDevice(), allocations[allocationIndex],
								0, VK_WHOLE_SIZE, 0, pHostAddress
						), "MapMemory", name);
						hostAddresses[allocationIndex] = pHostAddress.get(0);
					}

					block.allocations.add(new MemoryBlock.MemoryAllocation(
							allocations[allocationIndex], size, memoryType, priority, hostAddresses[allocationIndex]
					));
				}

				for (var buffer : buffers) {
					for (var claim : buffer.claims) {
						if (buffer.groupedClaims.get(claim.groupIndex).allocationIndex == allocationIndex) {
							claim.buffer.memoryTypeIndex = memoryType;
						}
					}
				}
			}
		}

		for (BufferUsageClaims buffer : buffers) {
			for (int groupIndex = 0; groupIndex < buffer.groupedClaims.size(); groupIndex++) {
				int finalGroupIndex = groupIndex;
				var bufferGroup = buffer.groupedClaims.get(groupIndex);
				@SuppressWarnings("OptionalGetWithoutIsPresent")
				long vkBuffer = buffer.claims.stream().filter(
						claim -> claim.groupIndex == finalGroupIndex
				).findAny().get().buffer.vkBuffer;
				block.vkBuffers.add(vkBuffer);
				if (useVma) {
					assertVmaSuccess(vmaBindBufferMemory2(
							instance.vmaAllocator(), allocations[bufferGroup.allocationIndex],
							bufferGroup.memoryOffset, vkBuffer, NULL
					), "BindBufferMemory2", name + ": buffer");
				} else {
					assertVkSuccess(vkBindBufferMemory(
							instance.vkDevice(), vkBuffer,
							allocations[bufferGroup.allocationIndex], bufferGroup.memoryOffset
					), "BindBufferMemory", name + ": buffer");
				}
				if (bufferGroup.shouldMapMemory) {
					buffer.setHostAddress(groupIndex, hostAddresses[bufferGroup.allocationIndex]);
				}
			}
		}

		for (ImageClaim image : images) {
			block.vkImages.add(image.image.vkImage);
			if (useVma) {
				assertVmaSuccess(vmaBindImageMemory2(
						instance.vmaAllocator(), allocations[image.allocationIndex],
						image.memoryOffset, image.image.vkImage, NULL
				), "BindImageMemory2", name + ": " + image);
			} else {
				assertVkSuccess(vkBindImageMemory(
						instance.vkDevice(), image.image.vkImage,
						allocations[image.allocationIndex], image.memoryOffset
				), "BindImageMemory", name + ": " + image);
			}

			if (image.builder.shouldCreateView) {
				image.image.vkImageView = image.builder.createView(instance, image.image.vkImage);
				block.vkImageViews.add(image.image.vkImageView);
			}
		}
	}
}
