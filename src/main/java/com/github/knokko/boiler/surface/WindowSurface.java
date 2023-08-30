package com.github.knokko.boiler.surface;

import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;

import java.util.Set;

import static org.lwjgl.vulkan.KHRSurface.vkDestroySurfaceKHR;

public record WindowSurface(
        long vkSurface,
        Set<SurfaceFormat> formats,
        Set<Integer> presentModes,
        VkSurfaceCapabilitiesKHR capabilities
) {
    public void destroy(VkInstance vkInstance) {
        vkDestroySurfaceKHR(vkInstance, vkSurface, null);
        capabilities.free();
    }
}
