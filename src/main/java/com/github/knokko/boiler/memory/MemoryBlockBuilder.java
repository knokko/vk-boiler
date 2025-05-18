package com.github.knokko.boiler.memory;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.buffers.MappedVkbBuffer;
import com.github.knokko.boiler.buffers.VkbBuffer;
import com.github.knokko.boiler.images.ImageBuilder;
import com.github.knokko.boiler.images.VkbImage;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class MemoryBlockBuilder {

	private final BoilerInstance instance;
	private final String name;
	final Map<BufferUsageKey, BufferUsageClaims> buffers = new HashMap<>();
	final Collection<ImageClaim> images = new ArrayList<>();
	final MemoryTypeClaims[] claims;

	public MemoryBlockBuilder(BoilerInstance instance, String name) {
		this.instance = instance;
		this.name = name;
		this.claims = new MemoryTypeClaims[instance.memoryInfo.numMemoryTypes];
	}

	private MemoryTypeClaims getClaims(int memoryTypeIndex) {
		if (claims[memoryTypeIndex] == null) claims[memoryTypeIndex] = new MemoryTypeClaims();
		return claims[memoryTypeIndex];
	}

	public VkbBuffer addBuffer(long size, long alignment, int usage) {
		VkbBuffer buffer = new VkbBuffer(size);
		buffers.computeIfAbsent(
				new BufferUsageKey(usage, false), key -> new BufferUsageClaims()
		).claims.add(new BufferClaim(buffer, alignment));
		return buffer;
	}

	// TODO try bar?
	public MappedVkbBuffer addMappedBuffer(long size, long alignment, int usage) {
		MappedVkbBuffer buffer = new MappedVkbBuffer(size);
		buffers.computeIfAbsent(
				new BufferUsageKey(usage, true), key -> new BufferUsageClaims()
		).claims.add(new BufferClaim(buffer, alignment));
		return buffer;
	}

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
				String bufferName = name + (key.hostVisible() ? (": mapped buffer usage " + key.usage()) :
						(": buffer usage " + key.usage()));

				ciBuffer.size(size);
				ciBuffer.usage(key.usage());

				assertVkSuccess(vkCreateBuffer(
						instance.vkDevice(), ciBuffer, null, pBuffer
				), "CreateBuffer", bufferName);
				long vkBuffer = pBuffer.get(0);
				instance.debug.name(stack, vkBuffer, VK_OBJECT_TYPE_BUFFER, name);

				vkGetBufferMemoryRequirements(instance.vkDevice(), vkBuffer, requirements);
				claim.setBuffer(vkBuffer, requirements.size(), requirements.alignment());

				int memoryTypeIndex = key.hostVisible() ?
						instance.memoryInfo.recommendedHostVisibleMemoryType(requirements.memoryTypeBits()) :
						instance.memoryInfo.recommendedDeviceLocalMemoryType(requirements.memoryTypeBits());
				getClaims(memoryTypeIndex).buffers.add(claim);
			});
			buffers.clear();
		}
	}

	public MemoryBlock allocate(boolean useVma) {
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
