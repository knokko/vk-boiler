package com.github.knokko.boiler.window;

import com.github.knokko.boiler.surface.SurfaceFormat;

public record SwapchainSettings(int imageUsage, SurfaceFormat surfaceFormat, int compositeAlpha) {
}
