package com.github.knokko.boiler.builder.device;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.VK10.*;

public record SimpleDeviceSelector(int... preferredDeviceTypes) implements PhysicalDeviceSelector {

	@Override
	public VkPhysicalDevice choosePhysicalDevice(MemoryStack stack, VkPhysicalDevice[] candidates, VkInstance vkInstance) {
		int[] deviceTypes = new int[candidates.length];
		var deviceProperties = VkPhysicalDeviceProperties.calloc(stack);
		for (int index = 0; index < candidates.length; index++) {
			vkGetPhysicalDeviceProperties(candidates[index], deviceProperties);
			deviceTypes[index] = deviceProperties.deviceType();
		}

		for (int preferredType : preferredDeviceTypes) {
			for (int index = 0; index < deviceTypes.length; index++) {
				if (deviceTypes[index] == preferredType) {
					return candidates[index];
				}
			}
		}

		return candidates[0];
	}
}
