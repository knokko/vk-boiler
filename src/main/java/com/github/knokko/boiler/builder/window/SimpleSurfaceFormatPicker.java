package com.github.knokko.boiler.builder.window;


import java.util.Set;

import static org.lwjgl.vulkan.KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR;

public class SimpleSurfaceFormatPicker implements SurfaceFormatPicker {

    private final int[] preferredVkFormats;

    public SimpleSurfaceFormatPicker(int... preferredVkFormats) {
        this.preferredVkFormats = preferredVkFormats;
    }

    @Override
    public SurfaceFormat chooseSurfaceFormat(Set<SurfaceFormat> availableSurfaceFormats) {
        for (int vkFormat : preferredVkFormats) {
            var desiredFormat = new SurfaceFormat(vkFormat, VK_COLOR_SPACE_SRGB_NONLINEAR_KHR);
            if (availableSurfaceFormats.contains(desiredFormat)) return desiredFormat;
        }

        return availableSurfaceFormats.iterator().next();
    }
}
