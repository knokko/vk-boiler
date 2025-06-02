package com.github.knokko.boiler.builders;

import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.util.*;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.VK_QUEUE_GRAPHICS_BIT;

class BasicDeviceFilter {

	private static Set<String> getSupportedDeviceExtensions(VkPhysicalDevice vkPhysicalDevice) {
		try (var stack = stackPush()) {
			var pNumExtensions = stack.callocInt(1);
			assertVkSuccess(vkEnumerateDeviceExtensionProperties(
					vkPhysicalDevice, (ByteBuffer) null, pNumExtensions, null
			), "EnumerateDeviceExtensionProperties", "BasicDeviceFilter count");
			int numExtensions = pNumExtensions.get(0);

			// NOTE: Do NOT allocate this on the stack because this array can be dangerously large for the
			// small default LWJGL stack
			var pExtensions = VkExtensionProperties.calloc(numExtensions);
			assertVkSuccess(vkEnumerateDeviceExtensionProperties(
					vkPhysicalDevice, (ByteBuffer) null, pNumExtensions, pExtensions
			), "EnumerateDeviceExtensionProperties", "BasicDeviceFilter extensions");

			var extensions = new HashSet<String>(numExtensions);
			for (int index = 0; index < numExtensions; index++) {
				extensions.add(pExtensions.get(index).extensionNameString());
			}
			pExtensions.free();
			return extensions;
		}
	}

	private static String supportsRequiredFeatures(VkPhysicalDevice device, BoilerBuilder builder) {
		try (var stack = stackPush()) {
			var supportedFeatures = SupportedFeatures.query(
					stack, device, builder.apiVersion,
					!builder.vkRequiredFeatures10.isEmpty(), !builder.vkRequiredFeatures11.isEmpty(),
					!builder.vkRequiredFeatures12.isEmpty(), !builder.vkRequiredFeatures13.isEmpty(),
					!builder.vkRequiredFeatures14.isEmpty()
			);
			for (var requirement : builder.vkRequiredFeatures10) {
				if (!requirement.predicate().test(supportedFeatures.features10())) return requirement.description();
			}
			for (var requirement : builder.vkRequiredFeatures11) {
				if (!requirement.predicate().test(supportedFeatures.features11())) return requirement.description();
			}
			for (var requirement : builder.vkRequiredFeatures12) {
				if (!requirement.predicate().test(supportedFeatures.features12())) return requirement.description();
			}
			for (var requirement : builder.vkRequiredFeatures13) {
				if (!requirement.predicate().test(supportedFeatures.features13())) return requirement.description();
			}
			for (var requirement : builder.vkRequiredFeatures14) {
				if (!requirement.predicate().test(supportedFeatures.features14())) return requirement.description();
			}
			return null;
		}
	}

	static VkPhysicalDevice[] getCandidates(
			BoilerBuilder builder, VkInstance vkInstance,
			long[] windowSurfaces, boolean printSelectionInfo
	) {
		try (var stack = stackPush()) {
			var pNumDevices = stack.callocInt(1);
			assertVkSuccess(vkEnumeratePhysicalDevices(
					vkInstance, pNumDevices, null
			), "EnumeratePhysicalDevices", "BasicDeviceFilter count");
			int numDevices = pNumDevices.get(0);

			var pDevices = stack.callocPointer(numDevices);
			assertVkSuccess(vkEnumeratePhysicalDevices(
					vkInstance, pNumDevices, pDevices
			), "EnumeratePhysicalDevices", "BasicDeviceFilter devices");

			var devices = new ArrayList<VkPhysicalDevice>(numDevices);

			int desiredMajorVersion = VK_API_VERSION_MAJOR(builder.apiVersion);
			int desiredMinorVersion = VK_API_VERSION_MINOR(builder.apiVersion);

			deviceLoop:
			for (int index = 0; index < numDevices; index++) {
				var device = new VkPhysicalDevice(pDevices.get(index), vkInstance);
				var properties = VkPhysicalDeviceProperties.calloc(stack);
				vkGetPhysicalDeviceProperties(device, properties);

				int supportedMajorVersion = VK_API_VERSION_MAJOR(properties.apiVersion());
				int supportedMinorVersion = VK_API_VERSION_MINOR(properties.apiVersion());
				if (supportedMajorVersion < desiredMajorVersion ||
						(supportedMajorVersion == desiredMajorVersion && supportedMinorVersion < desiredMinorVersion)
				) {
					if (printSelectionInfo) {
						System.out.println(
								"BasicDeviceFilter: rejected " + properties.deviceNameString() +
										" because it doesn't support Vulkan " + desiredMajorVersion +
										"." + desiredMinorVersion
						);
					}
					continue;
				}

				String missingFeature = supportsRequiredFeatures(device, builder);
				if (missingFeature != null) {
					if (printSelectionInfo) {
						System.out.println("BasicDeviceFilter: rejected " + properties.deviceNameString() +
								" because it doesn't support the required feature " + missingFeature);
					}
					continue;
				}

				var supportedExtensions = getSupportedDeviceExtensions(device);
				for (String extension : builder.requiredVulkanDeviceExtensions) {
					if (!supportedExtensions.contains(extension)) {
						if (printSelectionInfo) {
							System.out.println("BasicDeviceFilter: rejected " + properties.deviceNameString() +
									" because it doesn't support the extension " + extension);
						}
						continue deviceLoop;
					}
				}

				// canPresentToSurfaces[i] is true if and only if at least 1 queue family of the device can present to it
				boolean[] canPresentToSurfaces = new boolean[builder.windows.size()];
				boolean hasGraphicsQueueFamily = false;

				var pNumQueueFamilies = stack.callocInt(1);
				vkGetPhysicalDeviceQueueFamilyProperties(device, pNumQueueFamilies, null);
				int numQueueFamilies = pNumQueueFamilies.get(0);
				var pQueueFamilies = VkQueueFamilyProperties.calloc(numQueueFamilies, stack);
				vkGetPhysicalDeviceQueueFamilyProperties(device, pNumQueueFamilies, pQueueFamilies);

				var pPresentSupport = stack.callocInt(1);
				for (int queueFamilyIndex = 0; queueFamilyIndex < numQueueFamilies; queueFamilyIndex++) {

					for (int surfaceIndex = 0; surfaceIndex < windowSurfaces.length; surfaceIndex++) {
						assertVkSuccess(vkGetPhysicalDeviceSurfaceSupportKHR(
								device, queueFamilyIndex, windowSurfaces[surfaceIndex], pPresentSupport
						), "GetPhysicalDeviceSurfaceSupportKHR", "BasicDeviceFilter");
						if (pPresentSupport.get(0) == VK_TRUE) canPresentToSurfaces[surfaceIndex] = true;
					}

					if ((pQueueFamilies.get(queueFamilyIndex).queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) {
						hasGraphicsQueueFamily = true;
					}
				}

				boolean hasPresentQueueFamily = true;
				for (boolean canPresent : canPresentToSurfaces) {
					if (!canPresent) {
						hasPresentQueueFamily = false;
						break;
					}
				}

				if (!hasPresentQueueFamily || !hasGraphicsQueueFamily) {
					if (printSelectionInfo) {
						System.out.println("BasicDeviceFilter: rejected " + properties.deviceNameString()
								+ " because it doesn't have all required queue families: present = "
								+ hasPresentQueueFamily + ", graphics = " + hasGraphicsQueueFamily);
					}
					continue;
				}

				List<String> missedExtraRequirements = builder.extraDeviceRequirements.stream().map(requirements -> {
					if (requirements.requirements().satisfiesRequirements(device, windowSurfaces, stack)) return null;
					else return requirements.description();
				}).filter(Objects::nonNull).toList();
				if (!missedExtraRequirements.isEmpty()) {
					if (printSelectionInfo) {
						System.out.println("BasicDeviceFilter: rejected " + properties.deviceNameString()
								+ " because it didn't satisfy the extra device requirements " + missedExtraRequirements);
					}
					continue;
				}

				if (printSelectionInfo) System.out.println("BasicDeviceFilter: accepted " + properties.deviceNameString() + " (" + device.address() + ")");
				devices.add(device);
			}

			return devices.toArray(new VkPhysicalDevice[0]);
		}
	}
}
