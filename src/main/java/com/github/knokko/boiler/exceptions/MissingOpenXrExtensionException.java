package com.github.knokko.boiler.exceptions;

public class MissingOpenXrExtensionException extends RuntimeException {

	public final String extensionName;

	public MissingOpenXrExtensionException(String extensionName) {
		super("OpenXR extension \"" + extensionName + "\" is required, but not supported");
		this.extensionName = extensionName;
	}
}
