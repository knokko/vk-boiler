package com.github.knokko.boiler.builders.device;

import org.lwjgl.vulkan.VkPhysicalDeviceVulkan13Features;

import java.util.function.Predicate;

public record RequiredFeatures13(String description, Predicate<VkPhysicalDeviceVulkan13Features> predicate) {
}
