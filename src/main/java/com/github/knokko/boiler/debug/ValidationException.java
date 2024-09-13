package com.github.knokko.boiler.debug;

/**
 * This exception will be thrown whenever vk-boiler encounters an unexpected non-zero result of a Vulkan function call.
 */
public class ValidationException extends RuntimeException {

	public ValidationException(String message) {
		super(message);
	}
}
