package com.github.knokko.boiler.builder.device;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDevice;

import java.util.Set;

@FunctionalInterface
public interface VkDeviceCreator {

    VkDevice vkCreateDevice(
            MemoryStack stack, VkPhysicalDevice physicalDevice,
            Set<String> deviceExtensions, VkDeviceCreateInfo ciDevice
    );
}
