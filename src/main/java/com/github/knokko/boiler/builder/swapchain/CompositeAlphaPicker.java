package com.github.knokko.boiler.builder.swapchain;

@FunctionalInterface
public interface CompositeAlphaPicker {

    int chooseCompositeAlpha(int availableMask);
}
