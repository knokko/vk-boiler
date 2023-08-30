package com.github.knokko.boiler.builder.swapchain;

import com.github.knokko.boiler.surface.SurfaceFormat;

import java.util.Set;

@FunctionalInterface
public interface SurfaceFormatPicker {

    SurfaceFormat chooseSurfaceFormat(Set<SurfaceFormat> availableSurfaceFormats);
}
