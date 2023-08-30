package com.github.knokko.boiler.builder.device;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkPhysicalDevice;

import java.util.Set;

@FunctionalInterface
public interface PhysicalDeviceSelector {

    VkPhysicalDevice choosePhysicalDevice(
            MemoryStack stack, VkInstance vkInstance, long windowSurface,
            Set<String> requiredDeviceExtensions
    );
}
