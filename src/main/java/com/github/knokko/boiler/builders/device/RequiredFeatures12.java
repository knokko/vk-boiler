package com.github.knokko.boiler.builders.device;

import org.lwjgl.vulkan.VkPhysicalDeviceVulkan12Features;

import java.util.function.Predicate;

public record RequiredFeatures12(String description, Predicate<VkPhysicalDeviceVulkan12Features> predicate) {
}
