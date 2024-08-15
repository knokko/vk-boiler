package com.github.knokko.boiler.builder.device;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPhysicalDevice;

@FunctionalInterface
public interface ExtraDeviceRequirements {

    boolean satisfiesRequirements(VkPhysicalDevice physicalDevice, long[] windowSurfaces, MemoryStack stack);
}
