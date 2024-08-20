package com.github.knokko.boiler.builder.window;

import java.util.Set;

@FunctionalInterface
public interface SurfaceFormatPicker {

	SurfaceFormat chooseSurfaceFormat(Set<SurfaceFormat> availableSurfaceFormats);
}
