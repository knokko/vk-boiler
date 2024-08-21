package com.github.knokko.boiler.exceptions;

public class MissingOpenXrLayerException extends RuntimeException {

	public final String layerName;

	public MissingOpenXrLayerException(String layerName) {
		super("OpenXR layer \"" + layerName + "\" is required, but not supported");
		this.layerName = layerName;
	}
}
