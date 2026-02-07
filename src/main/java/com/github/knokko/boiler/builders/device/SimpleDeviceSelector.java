package com.github.knokko.boiler.builders.device;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.vulkan.KHRPortabilitySubset.VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK10.*;

/**
 * An implementation of <i>PhysicalDeviceSelector</i> that makes its decision based on their device types. It will also
 * avoid non-conformant devices (MoltenVK) when it can find a conformant device with the same type.
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

		int numBestDevices = 0;
		int bestDeviceType = -1;
		for (int preferredType : preferredDeviceTypes) {
			for (int deviceType : deviceTypes) {
				if (deviceType == preferredType) numBestDevices += 1;
			}
			if (numBestDevices > 0) {
				bestDeviceType = preferredType;
				break;
			}
		}

		// If we have multiple candidates of the same physical device type, pick the first one with full Vulkan support
		if (numBestDevices > 1 || bestDeviceType == -1) {
			for (int deviceIndex = 0; deviceIndex < candidates.length; deviceIndex++) {
				if (deviceTypes[deviceIndex] != bestDeviceType && bestDeviceType != -1) continue;
				var pNumSupportedExtensions = stack.callocInt(1);
				assertVkSuccess(vkEnumerateDeviceExtensionProperties(
						candidates[deviceIndex], (ByteBuffer) null, pNumSupportedExtensions, null
				), "EnumerateDeviceExtensionProperties", "SimpleDeviceSelector count");
				int numSupportedExtensions = pNumSupportedExtensions.get(0);

				var pSupportedExtensions = VkExtensionProperties.calloc(numSupportedExtensions);
				assertVkSuccess(vkEnumerateDeviceExtensionProperties(
						candidates[deviceIndex], (ByteBuffer) null, pNumSupportedExtensions, pSupportedExtensions
				), "EnumerateDeviceExtensionProperties", "SimpleDeviceSelector extensions");

				boolean hasFullSupport = true;
				for (int index = 0; index < numSupportedExtensions; index++) {
					var extension = pSupportedExtensions.get(index).extensionNameString();
					if (extension.equals(VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME)) {
						hasFullSupport = false;
						break;
					}
				}
				pSupportedExtensions.free();

				if (hasFullSupport) return candidates[deviceIndex];
			}
		}

		// If we have only 1 device, or all of them require portability, just pick the first one with the right type
		for (int deviceIndex = 0; deviceIndex < candidates.length; deviceIndex++) {
			if (deviceTypes[deviceIndex] == bestDeviceType) return candidates[deviceIndex];
		}

		// If none of them has any preferred device type, just return the first one
		return candidates[0];
	}
}
