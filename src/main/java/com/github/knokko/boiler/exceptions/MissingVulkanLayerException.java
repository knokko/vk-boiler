package com.github.knokko.boiler.exceptions;

public class MissingVulkanLayerException extends RuntimeException {

    public final String layerName;

    public MissingVulkanLayerException(String layerName) {
        super("Vulkan layer \"" + layerName + "\" is required, but not supported");
        this.layerName = layerName;
    }
}
