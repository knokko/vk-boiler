package com.github.knokko.boiler.builders.device;

import org.lwjgl.vulkan.VkPhysicalDeviceFeatures;

import java.util.function.Predicate;

public record RequiredFeatures10(String description, Predicate<VkPhysicalDeviceFeatures> predicate) {
}
