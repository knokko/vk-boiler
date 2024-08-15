package com.github.knokko.boiler.builder.window;

import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;

import java.util.Set;

public record WindowSurface(
        long vkSurface,
        Set<SurfaceFormat> formats,
        Set<Integer> presentModes,
        VkSurfaceCapabilitiesKHR capabilities
) {}
