package com.github.knokko.boiler.builders;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.vkGetPhysicalDeviceFeatures2;

record SupportedFeatures(
		VkPhysicalDeviceFeatures features10, VkPhysicalDeviceVulkan11Features features11,
		VkPhysicalDeviceVulkan12Features features12, VkPhysicalDeviceVulkan13Features features13,
		VkPhysicalDeviceVulkan14Features features14
) {
	static SupportedFeatures query(
			MemoryStack stack, VkPhysicalDevice device, int apiVersion,
			boolean check10, boolean check11, boolean check12, boolean check13, boolean check14
	) {
		if (VK_API_VERSION_MAJOR(apiVersion) != 1) {
			throw new UnsupportedOperationException("Unknown api major version: " + VK_API_VERSION_MAJOR(apiVersion));
		}
		int minorVersion = VK_API_VERSION_MINOR(apiVersion);

		if (minorVersion == 0) {
			if (check10) {
				var supportedFeatures = VkPhysicalDeviceFeatures.calloc(stack);
				vkGetPhysicalDeviceFeatures(device, supportedFeatures);
				return new SupportedFeatures(supportedFeatures, null, null, null, null);
			} else return new SupportedFeatures(null, null, null, null, null);
		} else {
			var supportedFeatures = VkPhysicalDeviceFeatures2.calloc(stack);
			supportedFeatures.sType$Default();

			VkPhysicalDeviceVulkan11Features supportedFeatures11 = null;
			VkPhysicalDeviceVulkan12Features supportedFeatures12 = null;
			VkPhysicalDeviceVulkan13Features supportedFeatures13 = null;
			VkPhysicalDeviceVulkan14Features supportedFeatures14 = null;
			if (minorVersion >= 1 && check11) {
				supportedFeatures11 = VkPhysicalDeviceVulkan11Features.calloc(stack);
				supportedFeatures11.sType$Default();
				supportedFeatures.pNext(supportedFeatures11);
			}
			if (minorVersion >= 2 && check12) {
				supportedFeatures12 = VkPhysicalDeviceVulkan12Features.calloc(stack);
				supportedFeatures12.sType$Default();
				supportedFeatures.pNext(supportedFeatures12);
			}
			if (minorVersion >= 3 && check13) {
				supportedFeatures13 = VkPhysicalDeviceVulkan13Features.calloc(stack);
				supportedFeatures13.sType$Default();
				supportedFeatures.pNext(supportedFeatures13);
			}
			if (minorVersion >= 4 && check14) {
				supportedFeatures14 = VkPhysicalDeviceVulkan14Features.calloc(stack);
				supportedFeatures14.sType$Default();
				supportedFeatures.pNext(supportedFeatures14);
			}

			vkGetPhysicalDeviceFeatures2(device, supportedFeatures);

			return new SupportedFeatures(
					supportedFeatures.features(), supportedFeatures11,
					supportedFeatures12, supportedFeatures13, supportedFeatures14
			);
		}
	}
}
