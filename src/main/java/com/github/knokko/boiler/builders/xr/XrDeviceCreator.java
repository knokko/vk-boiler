package com.github.knokko.boiler.builders.xr;

import com.github.knokko.boiler.builders.device.VkDeviceCreator;
import com.github.knokko.boiler.memory.callbacks.CallbackUserData;
import com.github.knokko.boiler.memory.callbacks.VkbAllocationCallbacks;
import org.lwjgl.openxr.XrInstance;
import org.lwjgl.openxr.XrVulkanDeviceCreateInfoKHR;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDevice;

import java.util.Set;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static com.github.knokko.boiler.exceptions.OpenXrFailureException.assertXrSuccess;
import static org.lwjgl.openxr.KHRVulkanEnable2.xrCreateVulkanDeviceKHR;

class XrDeviceCreator implements VkDeviceCreator {

	private final XrInstance xrInstance;
	private final long xrSystem;

	XrDeviceCreator(XrInstance xrInstance, long xrSystem) {
		this.xrInstance = xrInstance;
		this.xrSystem = xrSystem;
	}

	@Override
	public VkDevice vkCreateDevice(
			VkDeviceCreateInfo ciDevice, Set<String> instanceExtensions,
			VkPhysicalDevice physicalDevice, VkbAllocationCallbacks allocationCallbacks, MemoryStack stack
	) {
		var xrCiDevice = XrVulkanDeviceCreateInfoKHR.calloc(stack);
		xrCiDevice.type$Default();
		xrCiDevice.systemId(xrSystem);
		xrCiDevice.createFlags(0L);
		xrCiDevice.pfnGetInstanceProcAddr(VK.getFunctionProvider().getFunctionAddress("vkGetInstanceProcAddr"));
		xrCiDevice.vulkanPhysicalDevice(physicalDevice);
		xrCiDevice.vulkanCreateInfo(ciDevice);
		xrCiDevice.vulkanAllocator(CallbackUserData.DEVICE.put(stack, allocationCallbacks));

		var pDevice = stack.callocPointer(1);
		var pResult = stack.callocInt(1);
		assertXrSuccess(xrCreateVulkanDeviceKHR(
				xrInstance, xrCiDevice, pDevice, pResult
		), "CreateVulkanDeviceKHR", "XrDeviceCreator");
		assertVkSuccess(pResult.get(0), "xrCreateVulkanDeviceKHR", "XrDeviceSelector");

		return new VkDevice(pDevice.get(0), physicalDevice, ciDevice);
	}
}
