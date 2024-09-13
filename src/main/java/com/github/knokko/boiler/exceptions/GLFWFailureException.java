package com.github.knokko.boiler.exceptions;

/**
 * This exception will be thrown by <i>BoilerBuilder</i> and <i>WindowBuilder</i> when GLFW returns unexpected failures
 */
public class GLFWFailureException extends RuntimeException {

	public GLFWFailureException(String message) {
		super(message);
	}
}
