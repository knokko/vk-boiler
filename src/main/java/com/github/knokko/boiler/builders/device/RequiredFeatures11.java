package com.github.knokko.boiler.builders.device;

import org.lwjgl.vulkan.VkPhysicalDeviceVulkan11Features;

import java.util.function.Predicate;

public record RequiredFeatures11(String description, Predicate<VkPhysicalDeviceVulkan11Features> predicate) {
}
