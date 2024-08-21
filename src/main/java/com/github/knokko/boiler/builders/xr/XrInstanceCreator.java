package com.github.knokko.boiler.builders.xr;

import com.github.knokko.boiler.builders.instance.VkInstanceCreator;
import org.lwjgl.openxr.XrInstance;
import org.lwjgl.openxr.XrVulkanInstanceCreateInfoKHR;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static com.github.knokko.boiler.xr.OpenXrFailureException.assertXrSuccess;
import static org.lwjgl.openxr.KHRVulkanEnable2.xrCreateVulkanInstanceKHR;

class XrInstanceCreator implements VkInstanceCreator {

	private final XrInstance xrInstance;
	private final long xrSystem;

	XrInstanceCreator(XrInstance xrInstance, long xrSystem) {
		this.xrInstance = xrInstance;
		this.xrSystem = xrSystem;
	}

	@Override
	public VkInstance vkCreateInstance(VkInstanceCreateInfo ciInstance, MemoryStack stack) {
		var ciXrInstance = XrVulkanInstanceCreateInfoKHR.calloc(stack);
		ciXrInstance.type$Default();
		ciXrInstance.systemId(xrSystem);
		ciXrInstance.createFlags(0);
		ciXrInstance.pfnGetInstanceProcAddr(VK.getFunctionProvider().getFunctionAddress("vkGetInstanceProcAddr"));
		ciXrInstance.vulkanCreateInfo(ciInstance);
		ciXrInstance.vulkanAllocator(null);

		var pInstance = stack.callocPointer(1);
		var pResult = stack.callocInt(1);
		assertXrSuccess(xrCreateVulkanInstanceKHR(
				xrInstance, ciXrInstance, pInstance, pResult
		), "CreateVulkanInstanceKHR", "XrInstanceCreator");
		assertVkSuccess(pResult.get(0), "xrCreateVulkanInstanceKHR", "XrInstanceCreator");

		return new VkInstance(pInstance.get(0), ciInstance);
	}
}
