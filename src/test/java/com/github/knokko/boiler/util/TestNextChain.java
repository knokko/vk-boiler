package com.github.knokko.boiler.util;

import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
import static org.lwjgl.vulkan.VK11.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2;
import static org.lwjgl.vulkan.VK12.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_1_FEATURES;
import static org.lwjgl.vulkan.VK12.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES;
import static org.lwjgl.vulkan.VK13.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_3_FEATURES;

public class TestNextChain {

	@Test
	public void testFindAddressBasic() {
		try (var stack = stackPush()) {
			var features2 = VkPhysicalDeviceFeatures2.calloc(stack);
			features2.sType$Default();

			var features12 = VkPhysicalDeviceVulkan12Features.calloc(stack);
			features12.sType$Default();

			var features13 = VkPhysicalDeviceVulkan13Features.calloc(stack);
			features13.sType$Default();

			var ciDevice = VkDeviceCreateInfo.calloc(stack);
			ciDevice.sType$Default();

			ciDevice.pNext(features2);
			ciDevice.pNext(features12);
			ciDevice.pNext(features13);

			assertEquals(features2.address(), NextChain.findAddress(ciDevice.pNext(), VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2));
			assertEquals(features12.address(), NextChain.findAddress(ciDevice.pNext(), VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES));
			assertEquals(features13.address(), NextChain.findAddress(ciDevice.pNext(), VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_3_FEATURES));
			assertEquals(0L, NextChain.findAddress(ciDevice.pNext(), VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_1_FEATURES));
			assertEquals(0L, NextChain.findAddress(ciDevice.pNext(), VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO));
		}
	}

	@Test
	public void testFindAddressEmpty() {
		try (var stack = stackPush()) {
			var ciInstance = VkInstanceCreateInfo.calloc(stack);
			ciInstance.sType$Default();

			assertEquals(0L, NextChain.findAddress(ciInstance.pNext(), VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO));
			assertEquals(0L, NextChain.findAddress(ciInstance.pNext(), VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO));
		}
	}
}
