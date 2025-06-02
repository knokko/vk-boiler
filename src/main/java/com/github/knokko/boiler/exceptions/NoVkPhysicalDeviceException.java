package com.github.knokko.boiler.exceptions;

/**
 * This exception will be thrown when no <i>VkPhysicalDevice</i> satisfies all (your) requirements (or when the
 * computer doesn't have any graphics drivers that support Vulkan).
 */
public class NoVkPhysicalDeviceException extends RuntimeException {

	public NoVkPhysicalDeviceException() {
		super("No physical device satisfied all the requirements");
	}
}
