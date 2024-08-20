package com.github.knokko.boiler.exceptions;

public class MissingVulkanExtensionException extends RuntimeException {

	/**
	 * The type of extension that is missing, will be "instance" or "device"
	 */
	public final String extensionType;
	public final String extensionName;

	public MissingVulkanExtensionException(String extensionType, String extensionName) {
		super("Vulkan " + extensionType + " " + extensionName + " is required, but not supported");
		this.extensionType = extensionType;
		this.extensionName = extensionName;
	}
}
