package com.github.knokko.boiler.builders.xr;

import com.github.knokko.boiler.builders.device.PhysicalDeviceSelector;
import org.lwjgl.openxr.XrInstance;
import org.lwjgl.openxr.XrVulkanGraphicsDeviceGetInfoKHR;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;

import static com.github.knokko.boiler.exceptions.OpenXrFailureException.assertXrSuccess;
import static org.lwjgl.openxr.KHRVulkanEnable2.xrGetVulkanGraphicsDevice2KHR;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceProperties;

class XrDeviceSelector implements PhysicalDeviceSelector {

	private final XrInstance xrInstance;
	private final long xrSystem;

	XrDeviceSelector(XrInstance xrInstance, long xrSystem) {
		this.xrInstance = xrInstance;
		this.xrSystem = xrSystem;
	}

	@Override
	public VkPhysicalDevice choosePhysicalDevice(MemoryStack stack, VkPhysicalDevice[] candidates, VkInstance vkInstance) {
		var giDevice = XrVulkanGraphicsDeviceGetInfoKHR.calloc(stack);
		giDevice.type$Default();
		giDevice.systemId(xrSystem);
		giDevice.vulkanInstance(vkInstance);

		var pDevice = stack.callocPointer(1);
		assertXrSuccess(xrGetVulkanGraphicsDevice2KHR(
				xrInstance, giDevice, pDevice
		), "GetVulkanGraphicsDevice2KHR", "XrDeviceSelector");
		var device = new VkPhysicalDevice(pDevice.get(0), vkInstance);

		var properties = VkPhysicalDeviceProperties.calloc(stack);
		vkGetPhysicalDeviceProperties(device, properties);
		int vendorID = properties.vendorID();
		int deviceID = properties.deviceID();
		String deviceName = properties.deviceNameString();

		for (VkPhysicalDevice candidate : candidates) {
			vkGetPhysicalDeviceProperties(candidate, properties);
			if (vendorID == properties.vendorID() && deviceID == properties.deviceID()) return device;
		}

		System.out.println(
				"The OpenXR runtime requires physical device " + deviceName +
						", but this device doesn't satisfy the (application) requirements given to BoilderBuilder"
		);
		return null;
	}
}
