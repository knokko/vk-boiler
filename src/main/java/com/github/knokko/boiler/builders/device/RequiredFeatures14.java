package com.github.knokko.boiler.builders.device;

import org.lwjgl.vulkan.VkPhysicalDeviceVulkan14Features;

import java.util.function.Predicate;

public record RequiredFeatures14(String description, Predicate<VkPhysicalDeviceVulkan14Features> predicate) {
}
