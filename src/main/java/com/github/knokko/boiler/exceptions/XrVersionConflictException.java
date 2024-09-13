package com.github.knokko.boiler.exceptions;

/**
 * This exception will be thrown when the OpenXR runtime doesn't support the Vulkan API version that you passed to the
 * constructor of the <i>BoilerBuilder</i>.
 */
public class XrVersionConflictException extends RuntimeException {

	public XrVersionConflictException(String message) {
		super(message);
	}
}
