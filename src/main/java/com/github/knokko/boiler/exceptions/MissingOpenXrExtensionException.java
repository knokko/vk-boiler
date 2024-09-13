package com.github.knokko.boiler.exceptions;

/**
 * This exception will be thrown when a required OpenXR extension is not supported by the OpenXR runtime
 */
public class MissingOpenXrExtensionException extends RuntimeException {

	public final String extensionName;

	public MissingOpenXrExtensionException(String extensionName) {
		super("OpenXR extension \"" + extensionName + "\" is required, but not supported");
		this.extensionName = extensionName;
	}
}
