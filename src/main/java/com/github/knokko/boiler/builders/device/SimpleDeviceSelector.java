package com.github.knokko.boiler.builders.device;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.VK10.*;

/**
 * An implementation of <i>PhysicalDeviceSelector</i> that makes its decision based on their device types.
 * @param preferredDeviceTypes The physical device types, in order from favorite to the least favorite
 */
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
