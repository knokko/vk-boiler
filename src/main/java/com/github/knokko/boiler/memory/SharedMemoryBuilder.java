package com.github.knokko.boiler.memory;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.buffers.DeviceVkbBuffer;
import com.github.knokko.boiler.buffers.SharedMappedBufferBuilder;
import com.github.knokko.boiler.images.ImageBuilder;
import com.github.knokko.boiler.images.VkbImage;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaAllocationInfo;
import org.lwjgl.vulkan.*;

import java.util.*;
import java.util.function.Supplier;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVmaSuccess;
import static com.github.knokko.boiler.utilities.BoilerMath.leastCommonMultiple;
import static com.github.knokko.boiler.utilities.BoilerMath.nextMultipleOf;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK10.*;

/**
 * This class can be used to bundle multiple buffers and/or images into a single
 * <i>VkDeviceMemory</i> or <i>VmaAllocation</i>. To use this class, you should:
 * <ol>
 *     <li>Create an instance of <i>SharedMemoryBuilder</i> using its public constructor</li>
 *     <li>Call one of the {@link #add} methods for each buffer and image for which memory should be allocated</li>
 *     <li>Call the {@link #allocate} method to allocate the memory</li>
 * </ol>
 */
public class SharedMemoryBuilder {

	private final BoilerInstance instance;
	private final int numMemoryTypes;
	private final List<Integer> deviceLocalMemoryTypes = new ArrayList<>();
	private final List<Integer> hostVisibleMemoryTypes = new ArrayList<>();
	private final List<MemoryClaim> claims = new ArrayList<>();

	/**
	 * Creates a new instance of {@link SharedMemoryBuilder}, meant for a single allocation.
	 */
	public SharedMemoryBuilder(BoilerInstance instance) {
		this.instance = instance;
		try (var stack = stackPush()) {
			var memory = VkPhysicalDeviceMemoryProperties.calloc(stack);
			vkGetPhysicalDeviceMemoryProperties(instance.vkPhysicalDevice(), memory);
			this.numMemoryTypes = memory.memoryTypeCount();
			for (int index = 0; index < numMemoryTypes; index++) {
				int flags = memory.memoryTypes(index).propertyFlags();
				if ((flags & VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT) != 0) deviceLocalMemoryTypes.add(index);
				if ((flags & VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT) != 0 && (flags & VK_MEMORY_PROPERTY_HOST_COHERENT_BIT) != 0) {
					hostVisibleMemoryTypes.add(index);
				}
			}
		}
	}

	/**
	 * Ensures that the memory of {@code buffer} will be bound when {@link #allocate} is invoked. The memory type of the
	 * allocation will be determined by {@link #recommendedDeviceLocalMemoryType}.
	 */
	public void add(DeviceVkbBuffer buffer) {
		try (var stack = stackPush()) {
			var requirements = VkMemoryRequirements.calloc(stack);
			vkGetBufferMemoryRequirements(instance.vkDevice(), buffer.vkBuffer(), requirements);

			int memoryType = recommendedDeviceLocalMemoryType(requirements.memoryTypeBits());
			claims.add(new MemoryClaim(
					memoryType, requirements.size(), requirements.alignment(),
					buffer.vkBuffer(), null, null
			));
		}
	}

	/**
	 * Ensures that {@code image} will be built when {@link #allocate} is invoked, and ensures that its memory will be
	 * bound. The memory type of the allocation will be determined by {@link #recommendedDeviceLocalMemoryType}.
	 * After {@link #allocate} has returned, you can call the {@link Supplier#get()} method of the returned supplier to
	 * obtain the created image.
	 */
	public Supplier<VkbImage> add(ImageBuilder image) {
		boolean shouldCreateView = image.shouldCreateView;
		var rawVkbImage = image.doNotBindMemory().build(instance);
		try (var stack = stackPush()) {
			var requirements = VkMemoryRequirements.calloc(stack);
			vkGetImageMemoryRequirements(instance.vkDevice(), rawVkbImage.vkImage(), requirements);

			int memoryType = recommendedDeviceLocalMemoryType(requirements.memoryTypeBits());
			RawImageClaim claim = new RawImageClaim(image, rawVkbImage, shouldCreateView);
			claims.add(new MemoryClaim(
					memoryType, requirements.size(), requirements.alignment(), VK_NULL_HANDLE, claim, null
			));
			return () -> claim.result;
		}
	}

	/**
	 * Ensures that {@code buffer} will be built when {@link #allocate} is invoked, and ensures that its memory will be
	 * bound. The memory type of the allocation will be determined by {@link #recommendedHostVisibleMemoryType}.
	 * @param buffer The buffer that should be built and bound when {@link #allocate} is invoked
	 * @param bufferUsage The buffer usage flags for {@link VkBufferCreateInfo#usage()}
	 * @param name The debug name for the <i>VkBuffer</i> that will be created for {@code buffer}
	 */
	public void add(SharedMappedBufferBuilder buffer, int bufferUsage, String name) {
		try (var stack = stackPush()) {
			long vkBuffer = buffer.buildRaw(bufferUsage, name);
			var requirements = VkMemoryRequirements.calloc(stack);
			vkGetBufferMemoryRequirements(instance.vkDevice(), vkBuffer, requirements);

			var alignments = buffer.getAlignments();
			alignments.add(requirements.alignment());
			requirements.alignment(leastCommonMultiple(alignments));

			int memoryType = recommendedHostVisibleMemoryType(requirements.memoryTypeBits());
			claims.add(new MemoryClaim(
					memoryType, requirements.size(), requirements.alignment(), vkBuffer, null, buffer
			));
		}
	}

	/**
	 * @param memoryTypeBits {@link VkMemoryRequirements#memoryTypeBits()}
	 * @return The first device-local memory property that is allowed by {@code memoryTypeBits}, or the first memory
	 * property allowed by {@code memoryTypeBits} when no device-local memory property is allowed
	 * @throws UnsupportedOperationException when {@code memoryTypeBits == 0}
	 */
	public int recommendedDeviceLocalMemoryType(int memoryTypeBits) throws UnsupportedOperationException {
		for (int index : deviceLocalMemoryTypes) {
			if ((memoryTypeBits & (1 << index)) != 0) return index;
		}
		for (int index = 0; index < numMemoryTypes; index++) {
			if ((memoryTypeBits & (1 << index)) != 0) return index;
		}
		throw new UnsupportedOperationException("Not a single supported memory type? " + memoryTypeBits);
	}

	/**
	 * @param memoryTypeBits {@link VkMemoryRequirements#memoryTypeBits()}
	 * @return The first host-visible memory property that is allowed by {@code memoryTypeBits}
	 * @throws UnsupportedOperationException when not a single host-visible memory property is allowed by
	 * {@code memoryTypeBits}
	 */
	public int recommendedHostVisibleMemoryType(int memoryTypeBits) throws UnsupportedOperationException {
		for (int index : hostVisibleMemoryTypes) {
			if ((memoryTypeBits & (1 << index)) != 0) return index;
		}
		throw new UnsupportedOperationException("No supported memory type is both HOST_VISIBLE and HOST_COHERENT");
	}

	/**
	 * Allocates the memory for all buffers and images that were added using one of the {@link #add} methods, and
	 * binds the memory of all these buffers and images.
	 * @param name The debug name of the allocated <i>VkDeviceMemory</i>s (when {@code useVma} is false)
	 * @param useVma True if {@link org.lwjgl.util.vma.Vma#vmaAllocateMemory} should be used, false if
	 *               {@link org.lwjgl.vulkan.VK10#vkAllocateMemory} should be used
	 * @return a shared allocation that should be {@link SharedMemoryAllocations#free}d when you want to free the
	 * allocated memory
	 */
	public SharedMemoryAllocations allocate(String name, boolean useVma) {
		long[] allocationSizes = new long[numMemoryTypes];
		long[] allocationMapping = new long[numMemoryTypes];
		long[] hostAddresses = new long[numMemoryTypes];

		Collections.sort(claims);
		for (MemoryClaim claim : claims) {
			claim.offset = nextMultipleOf(allocationSizes[claim.memoryType], claim.alignment);
			allocationSizes[claim.memoryType] = claim.offset + claim.size;
			if (claim.mappedBuilder != null) hostAddresses[claim.memoryType] = -1L;
		}

		List<Long> allocations = new ArrayList<>();
		List<Long> buffers = new ArrayList<>();
		List<VkbImage> images = new ArrayList<>();

		try (var stack = stackPush()) {
			var pMemory = stack.callocLong(1);
			var pVmaAllocation = stack.callocPointer(1);
			var pHostAddress = stack.callocPointer(1);
			var vmaRequirements = VkMemoryRequirements.calloc(stack);
			var ciVmaAllocation = VmaAllocationCreateInfo.calloc(stack);
			var vmaAllocationInfo = VmaAllocationInfo.calloc(stack);
			var aiMemory = VkMemoryAllocateInfo.calloc(stack);
			aiMemory.sType$Default();

			for (int memoryType = 0; memoryType < numMemoryTypes; memoryType++) {
				aiMemory.allocationSize(allocationSizes[memoryType]);
				if (aiMemory.allocationSize() == 0L) continue;
				aiMemory.memoryTypeIndex(memoryType);

				boolean mapMemory = hostAddresses[memoryType] == -1L;
				if (useVma) {
					Set<Long> alignments = new HashSet<>();
					for (MemoryClaim claim : claims) {
						if (claim.memoryType == memoryType) alignments.add(claim.alignment);
					}
					vmaRequirements.size(aiMemory.allocationSize());
					vmaRequirements.alignment(leastCommonMultiple(alignments));
					vmaRequirements.memoryTypeBits(1 << memoryType);
					ciVmaAllocation.flags(mapMemory ? VMA_ALLOCATION_CREATE_MAPPED_BIT : 0);
					ciVmaAllocation.memoryTypeBits(vmaRequirements.memoryTypeBits());
					assertVmaSuccess(vmaAllocateMemory(
							instance.vmaAllocator(), vmaRequirements, ciVmaAllocation, pVmaAllocation, vmaAllocationInfo
					), "AllocateMemory", name);

					long vmaAllocation = pVmaAllocation.get(0);
					allocations.add(vmaAllocation);
					allocationMapping[memoryType] = vmaAllocation;
					if (mapMemory) hostAddresses[memoryType] = vmaAllocationInfo.pMappedData();
				} else {
					assertVkSuccess(vkAllocateMemory(
							instance.vkDevice(), aiMemory, null, pMemory
					), "AllocateMemory", name);

					long vkMemory = pMemory.get(0);
					instance.debug.name(stack, vkMemory, VK_OBJECT_TYPE_DEVICE_MEMORY, name);
					allocations.add(vkMemory);
					allocationMapping[memoryType] = vkMemory;
					if (mapMemory) {
						assertVkSuccess(vkMapMemory(
								instance.vkDevice(), vkMemory, 0, VK_WHOLE_SIZE, 0, pHostAddress
						), "MapMemory", name);
						hostAddresses[memoryType] = pHostAddress.get(0);
					}
				}
			}
		}

		for (MemoryClaim claim : claims) {
			if (claim.vkBuffer != VK_NULL_HANDLE) {
				buffers.add(claim.vkBuffer);
				if (useVma) {
					assertVmaSuccess(vmaBindBufferMemory2(
							instance.vmaAllocator(), allocationMapping[claim.memoryType], claim.offset, claim.vkBuffer, NULL
					), "BindBufferMemory2", name);
				} else {
					assertVkSuccess(vkBindBufferMemory(
							instance.vkDevice(), claim.vkBuffer, allocationMapping[claim.memoryType], claim.offset
					), "BindBufferMemory", name);
				}
			} else {
				if (useVma) {
					assertVmaSuccess(vmaBindImageMemory2(
							instance.vmaAllocator(), allocationMapping[claim.memoryType],
							claim.offset, claim.image.raw.vkImage(), NULL
					), "BindImageMemory2", name);
				} else {
					assertVkSuccess(vkBindImageMemory(
							instance.vkDevice(), claim.image.raw.vkImage(),
							allocationMapping[claim.memoryType], claim.offset
					), "BindImageMemory", name);
				}

				long imageView = VK_NULL_HANDLE;
				if (claim.image.shouldCreateView) imageView = instance.images.createSimpleView(
						claim.image.raw.vkImage(), claim.image.builder.format,
						claim.image.builder.aspectMask, claim.image.builder.name
				);
				claim.image.result = new VkbImage(
						claim.image.raw.vkImage(), imageView, VK_NULL_HANDLE,
						claim.image.raw.width(), claim.image.raw.height(), claim.image.raw.aspectMask()
				);
				images.add(claim.image.result);
			}
			if (claim.mappedBuilder != null) {
				claim.mappedBuilder.setRaw(claim.vkBuffer, hostAddresses[claim.memoryType] + claim.offset);
			}
		}

		return new SharedMemoryAllocations(useVma, allocations, buffers, images);
	}

	private static class MemoryClaim implements Comparable<MemoryClaim> {

		final int memoryType;
		final long size, alignment;
		final long vkBuffer;
		final RawImageClaim image;
		final SharedMappedBufferBuilder mappedBuilder;

		long offset;

		MemoryClaim(
				int memoryType, long size, long alignment,
				long vkBuffer, RawImageClaim image, SharedMappedBufferBuilder mappedBuilder
		) {
			if (size <= 0 || alignment <= 0) throw new IllegalArgumentException("Size, and alignment must be positive");
			if (memoryType < 0) throw new IllegalArgumentException("Memory type must be non-negative");
			if ((vkBuffer == VK_NULL_HANDLE) == (image == null)) {
				throw new IllegalArgumentException("Exactly 1 of vkBuffer and vkImage must be null");
			}
			if (vkBuffer == VK_NULL_HANDLE && mappedBuilder != null) {
				throw new IllegalArgumentException("vkBuffer must not be VK_NULL_HANDLE when mappedBuilder is non-null");
			}
			this.memoryType = memoryType;
			this.size = size;
			this.alignment = alignment;
			this.vkBuffer = vkBuffer;
			this.image = image;
			this.mappedBuilder = mappedBuilder;
		}

		@Override
		public int compareTo(MemoryClaim other) {
			// Intentionally put claims with larger alignment first
			return Long.compare(other.alignment, this.alignment);
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof MemoryClaim && this.alignment == ((MemoryClaim) other).alignment;
		}

		@Override
		public int hashCode() {
			return (int) alignment;
		}
	}

	private static class RawImageClaim {

		final ImageBuilder builder;
		final VkbImage raw;
		final boolean shouldCreateView;

		VkbImage result;

		RawImageClaim(ImageBuilder builder, VkbImage raw, boolean shouldCreateView) {
			this.builder = builder;
			this.raw = raw;
			this.shouldCreateView = shouldCreateView;
		}
	}
}
