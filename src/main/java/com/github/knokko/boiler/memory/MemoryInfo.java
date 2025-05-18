package com.github.knokko.boiler.memory;

import com.github.knokko.boiler.BoilerInstance;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;

public class MemoryInfo {

	public final int numMemoryTypes;
	public final List<Integer> deviceLocalMemoryTypes;
	public final List<Integer> hostVisibleMemoryTypes;

	/**
	 * Note: this constructor is meant for internal use only. Use {@link BoilerInstance#memoryInfo} instead.
	 */
	public MemoryInfo(BoilerInstance instance) {
		try (var stack = stackPush()) {
			var memory = VkPhysicalDeviceMemoryProperties.calloc(stack);
			vkGetPhysicalDeviceMemoryProperties(instance.vkPhysicalDevice(), memory);
			this.numMemoryTypes = memory.memoryTypeCount();
			List<Integer> deviceLocalMemoryTypes = new ArrayList<>();
			List<Integer> hostVisibleMemoryTypes = new ArrayList<>();
			for (int index = 0; index < numMemoryTypes; index++) {
				int flags = memory.memoryTypes(index).propertyFlags();
				if ((flags & VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT) != 0) deviceLocalMemoryTypes.add(index);
				if ((flags & VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT) != 0 && (flags & VK_MEMORY_PROPERTY_HOST_COHERENT_BIT) != 0) {
					hostVisibleMemoryTypes.add(index);
				}
			}
			this.deviceLocalMemoryTypes = Collections.unmodifiableList(deviceLocalMemoryTypes);
			this.hostVisibleMemoryTypes = Collections.unmodifiableList(hostVisibleMemoryTypes);
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
}
