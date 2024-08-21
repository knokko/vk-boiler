package com.github.knokko.boiler.builders.window;

import java.util.Set;

@FunctionalInterface
public interface SurfaceFormatPicker {

	SurfaceFormat chooseSurfaceFormat(Set<SurfaceFormat> availableSurfaceFormats);
}
