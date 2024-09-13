package com.github.knokko.boiler.exceptions;

/**
 * This exception will be thrown when a required Vulkan layer is not supported
 */
public class MissingVulkanLayerException extends RuntimeException {

	public final String layerName;

	public MissingVulkanLayerException(String layerName) {
		super("Vulkan layer \"" + layerName + "\" is required, but not supported");
		this.layerName = layerName;
	}
}
