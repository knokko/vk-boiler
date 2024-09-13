package com.github.knokko.boiler.exceptions;

/**
 * This exception will be thrown when a required OpenXR layer is not supported by the OpenXR runtime
 */
public class MissingOpenXrLayerException extends RuntimeException {

	public final String layerName;

	public MissingOpenXrLayerException(String layerName) {
		super("OpenXR layer \"" + layerName + "\" is required, but not supported");
		this.layerName = layerName;
	}
}
