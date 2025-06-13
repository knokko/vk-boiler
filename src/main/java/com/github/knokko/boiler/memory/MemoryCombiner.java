package com.github.knokko.boiler.memory;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.buffers.MappedVkbBuffer;
import com.github.knokko.boiler.buffers.VkbBuffer;
import com.github.knokko.boiler.images.ImageBuilder;
import com.github.knokko.boiler.images.VkbImage;
import com.github.knokko.boiler.memory.callbacks.CallbackUserData;
import org.lwjgl.vulkan.*;

import java.util.HashMap;
import java.util.Map;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/**
 * <p>
 *  This class can be used to easily create buffers and images, and use a minimal number of memory allocations to bind
 *  all their memory. All buffers, images, and allocations will be created at the same time, and must be destroyed at
 *  the same time.
 * </p>
 * <p>
 *  You can choose whether {@link org.lwjgl.vulkan.VK10#vkAllocateMemory} or
 *  {@link org.lwjgl.util.vma.Vma#vmaAllocateMemory} will be used to create the memory allocations.
 * </p>
 * <ul>
 *     <li>
 *         When using VMA, VMA will probably create larger memory allocations than needed, but it will also reuse these
 *         allocations for future requests. The advantage of using MemoryCombiner with VMA over VMA without
 *         MemoryCombiner is that MemoryCombiner will reduce the VMA fragmentation since many buffers and images will
 *         be allocated using a single call to {@link org.lwjgl.util.vma.Vma#vmaAllocateMemory}.
 *     </li>
 *     <li>
 *         When not using VMA, the memory allocations will have exactly the right size, and cannot be used for future
 *         memory blocks.
 *     </li>
 * </ul>
 * <p>
 *     To use MemoryCombiner, you need to:
 * </p>
 * <ol>
 *     <li>Create an instance of MemoryCombiner using the public constructor.</li>
 *     <li>Call {@link #addBuffer} and {@link #addImage} for each buffer and image that you want to combine.</li>
 *     <li>Call {@link #build}</li>
 *     <li>
 *         Use the buffers and images. <b>Do <i>not</i> use them before calling {@link #build}!</b> Their memory will
 *         not be bound yet, and even the {@link VkbBuffer#vkBuffer} field can be {@link VK10#VK_NULL_HANDLE} before
 *         {@link #build} has been called!
 *     </li>
 *     <li>Call {@link MemoryBlock#destroy} to destroy all buffers, images, and allocations</li>
 * </ol>
 */
public class MemoryCombiner {

	private final BoilerInstance instance;
	private final String name;
	final Map<BufferUsageKey, BufferUsageClaims> buffers = new HashMap<>();
	final MemoryTypeClaims[] claims;

	/**
	 * Constructs a new empty memory combiner
	 * @param name The debug name, which is used for error reporting and validation
	 */
	public MemoryCombiner(BoilerInstance instance, String name) {
		this.instance = instance;
		this.name = name;
		this.claims = new MemoryTypeClaims[instance.memoryInfo.numMemoryTypes];
	}

	private MemoryTypeClaims getClaims(int memoryTypeIndex) {
		if (claims[memoryTypeIndex] == null) claims[memoryTypeIndex] = new MemoryTypeClaims();
		return claims[memoryTypeIndex];
	}

	/**
	 * Adds a {@link VkbBuffer} that will probably be <b>device-local</b>, and probably <i>not</i> <b>host-visible</b>.
	 * It will have the same `VkBuffer` as all other buffers added via this method, if their {@code usage} flags are
	 * the same.
	 * @param size The size of the buffer, in bytes
	 * @param alignment The alignment of the buffer, in bytes. The {@link VkbBuffer#offset} will be a multiple of
	 *                  {@code alignment}. Furthermore, the bound memory offset of the <b>VkBuffer</b> will be a
	 *                  multiple of {@code alignment}.
	 * @param usage The buffer usage flags: {@link VkBufferCreateInfo#usage()}
	 * @return The created {@link VkbBuffer}. <b>Note that its fields may be 0 until you call {@link #build}!</b>
	 */
	public VkbBuffer addBuffer(long size, long alignment, int usage) {
		VkbBuffer buffer = new VkbBuffer(size);
		buffers.computeIfAbsent(
				new BufferUsageKey(usage, false, false),
				key -> new BufferUsageClaims()
		).claims.add(new BufferClaim(buffer, alignment));
		return buffer;
	}

	/**
	 * Adds a {@link MappedVkbBuffer} that will certainly be <b>host-visible</b> and <b>host-coherent</b>, and will
	 * probably not be <b>device-local</b>. Its memory will be mapped as soon as you call {@link #build}.
	 * It will have the same `VkBuffer` as all other buffers added via this method, if their {@code usage} flags are
	 * the same.
	 * @param size The size of the buffer, in bytes
	 * @param alignment The alignment of the buffer, in bytes. The {@link MappedVkbBuffer#offset} will be a multiple of
	 *                  {@code alignment}. Furthermore, the bound memory offset of the <b>VkBuffer</b> will be a
	 *                  multiple of {@code alignment}.
	 * @param usage The buffer usage flags: {@link VkBufferCreateInfo#usage()}
	 * @return The created {@link MappedVkbBuffer}. <b>Note that its fields may be 0 until you call {@link #build}!</b>
	 */
	public MappedVkbBuffer addMappedBuffer(long size, long alignment, int usage) {
		MappedVkbBuffer buffer = new MappedVkbBuffer(size);
		buffers.computeIfAbsent(
				new BufferUsageKey(usage, true, false),
				key -> new BufferUsageClaims()
		).claims.add(new BufferClaim(buffer, alignment));
		return buffer;
	}

	/**
	 * Adds a {@link MappedVkbBuffer} that will certainly be <b>host-visible</b> and <b>host-coherent</b>,
	 * and preferably also <b>device-local</b>. Its memory will be mapped as soon as you call {@link #build}.
	 * It will have the same `VkBuffer` as all other buffers added via this method, if their {@code usage} flags are
	 * the same.
	 * @param size The size of the buffer, in bytes
	 * @param alignment The alignment of the buffer, in bytes. The {@link MappedVkbBuffer#offset} will be a multiple of
	 *                  {@code alignment}. Furthermore, the bound memory offset of the <b>VkBuffer</b> will be a
	 *                  multiple of {@code alignment}.
	 * @param usage The buffer usage flags: {@link VkBufferCreateInfo#usage()}
	 * @return The created {@link MappedVkbBuffer}. <b>Note that its fields may be 0 until you call {@link #build}!</b>
	 */
	public MappedVkbBuffer addMappedDeviceLocalBuffer(long size, long alignment, int usage) {
		MappedVkbBuffer buffer = new MappedVkbBuffer(size);
		buffers.computeIfAbsent(
				new BufferUsageKey(usage, true, true),
				key -> new BufferUsageClaims()
		).claims.add(new BufferClaim(buffer, alignment));
		return buffer;
	}

	/**
	 * Adds a {@link VkbImage} that will probably be <b>device-local</b>, and probably <i>not</i> <b>host-visible</b>.
	 * It will probably share the same memory allocation as the other images, as well as the device-local buffers.
	 * @param builder The {@link ImageBuilder} containing all the required image properties
	 * @return The created {@link VkbImage}. <b>Note that its memory will not be bound until you call {@link #build}!</b>
	 */
	public VkbImage addImage(ImageBuilder builder) {
		VkbImage image = builder.createRaw(instance);
		try (var stack = stackPush()) {
			var requirements = VkMemoryRequirements.calloc(stack);
			vkGetImageMemoryRequirements(instance.vkDevice(), image.vkImage, requirements);

			int memoryTypeIndex = builder.memoryTypeSelector.chooseMemoryType(instance, requirements.memoryTypeBits());
			getClaims(memoryTypeIndex).images.add(new ImageClaim(image, builder, requirements.size(), requirements.alignment()));
		}
		return image;
	}

	private void createBuffers() {
		try (var stack = stackPush()) {
			var ciBuffer = VkBufferCreateInfo.calloc(stack);
			ciBuffer.sType$Default();
			ciBuffer.flags(0);
			ciBuffer.sharingMode(VK_SHARING_MODE_EXCLUSIVE);

			var requirements = VkMemoryRequirements.calloc(stack);
			var pBuffer = stack.callocLong(1);

			buffers.forEach((key, claim) -> {
				long size = claim.computeSize();
				String bufferName = name + (key.hostVisible() ? (": mapped" +
						(key.preferablyDeviceLocal() ? " device" : "") + ") buffer usage " + key.usage()) :
						(": buffer usage " + key.usage()));

				ciBuffer.size(size);
				ciBuffer.usage(key.usage());

				assertVkSuccess(vkCreateBuffer(
						instance.vkDevice(), ciBuffer, CallbackUserData.BUFFER.put(stack, instance), pBuffer
				), "CreateBuffer", bufferName);
				long vkBuffer = pBuffer.get(0);
				instance.debug.name(stack, vkBuffer, VK_OBJECT_TYPE_BUFFER, name);

				vkGetBufferMemoryRequirements(instance.vkDevice(), vkBuffer, requirements);
				claim.setBuffer(vkBuffer, requirements.size(), requirements.alignment());

				int memoryTypeIndex;
				if (key.hostVisible()) {
					memoryTypeIndex = -1;
					if (key.preferablyDeviceLocal()) {
						memoryTypeIndex = instance.memoryInfo.recommendedHybridMemoryType(requirements.memoryTypeBits());
					}
					if (memoryTypeIndex == -1) {
						memoryTypeIndex = instance.memoryInfo.recommendedHostVisibleMemoryType(requirements.memoryTypeBits());
					}
				} else {
					memoryTypeIndex = instance.memoryInfo.recommendedDeviceLocalMemoryType(requirements.memoryTypeBits());
				}
				getClaims(memoryTypeIndex).buffers.add(claim);
			});
			buffers.clear();
		}
	}

	/**
	 * After adding all buffers and images, you should call this method to allocate their memory.
	 * @param useVma true to use {@link org.lwjgl.util.vma.Vma#vmaAllocateMemory}, false to use
	 *               {@link VK10#vkAllocateMemory}
	 */
	public MemoryBlock build(boolean useVma) {
		createBuffers();
		MemoryBlock block = new MemoryBlock();
		for (int index = 0; index < claims.length; index++) {
			MemoryTypeClaims claim = claims[index];
			if (claim == null) continue;
			claim.allocate(instance, name + ": memory type " + index, useVma, index, block);
		}
		return block;
	}
}
