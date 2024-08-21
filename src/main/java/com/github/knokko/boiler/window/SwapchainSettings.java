package com.github.knokko.boiler.window;

import com.github.knokko.boiler.builders.window.SurfaceFormat;

public record SwapchainSettings(int imageUsage, SurfaceFormat surfaceFormat, int compositeAlpha) {
}
