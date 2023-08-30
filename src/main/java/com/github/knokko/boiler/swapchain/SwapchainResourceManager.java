package com.github.knokko.boiler.swapchain;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class SwapchainResourceManager<T> {

    private final Function<AcquireResult, T> createResources;
    private final Consumer<T> destroyResources;
    private long currentSwapchainID;
    private List<T> currentResources;

    public SwapchainResourceManager(Function<AcquireResult, T> createResources, Consumer<T> destroyResources) {
        this.createResources = createResources;
        this.destroyResources = destroyResources;
    }

    public T get(AcquireResult swapchainImage) {
        if (currentResources == null || currentSwapchainID != swapchainImage.swapchainID()) {
            currentResources = new ArrayList<>(swapchainImage.numSwapchainImages());
            for (int counter = 0; counter < swapchainImage.numSwapchainImages(); counter++) {
                currentResources.add(null);
            }
            currentSwapchainID = swapchainImage.swapchainID();
        }

        var currentResource = currentResources.get(swapchainImage.imageIndex());
        if (currentResource == null) {
            currentResource = createResources.apply(swapchainImage);
            currentResources.set(swapchainImage.imageIndex(), currentResource);

            var rememberResource = currentResource;
            swapchainImage.addPreDestructionCallback().accept(() -> destroyResources.accept(rememberResource));
        }
        return currentResource;
    }
}
