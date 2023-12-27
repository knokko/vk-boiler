package com.github.knokko.boiler.builder.device;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDevice;

import java.util.Set;

@FunctionalInterface
public interface PreVkDeviceCreator {

    void beforeDeviceCreation(
            VkDeviceCreateInfo ciDevice,
            Set<String> enabledInstanceExtensions,
            VkPhysicalDevice physicalDevice,
            MemoryStack stack
    );
}
