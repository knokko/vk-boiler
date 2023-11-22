package com.github.knokko.boiler.builder.device;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDevice;

@FunctionalInterface
public interface PreVkDeviceCreator {

    void beforeDeviceCreation(VkDeviceCreateInfo ciDevice, VkPhysicalDevice physicalDevice, MemoryStack stack);
}
