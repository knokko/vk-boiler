package com.github.knokko.boiler.exceptions;

import com.github.knokko.boiler.util.ReflectionHelper;
import org.lwjgl.vulkan.VK12;

import static org.lwjgl.vulkan.VK10.*;

public class VulkanFailureException extends RuntimeException {

	public static void assertVkSuccess(int result, String functionName, String context, int... allowedResults) {
		if (result == VK_SUCCESS) return;
		for (int allowed : allowedResults) {
			if (result == allowed) return;
		}

		if (!functionName.startsWith("vk")) functionName = "vk" + functionName;
		throw new VulkanFailureException(functionName, result, context);
	}

	public static void assertVmaSuccess(int result, String functionName, String context) {
		if (result == VK_SUCCESS) return;

		if (!functionName.startsWith("vma")) functionName = "vma" + functionName;
		throw new VulkanFailureException(functionName, result, context);
	}

	static String generateMessage(String functionName, int result, String context) {

		// First try the constants starting with VK_ERROR_
		String returnCodeName = ReflectionHelper.getIntConstantName(
				VK12.class, result, "VK_ERROR_", "", "unknown"
		);

		// Unfortunately, the positive return codes don't start with VK_ERROR_, but may still be undesired. I'm
		// afraid checking them manually is the only right way to find them...
		if (returnCodeName.equals("unknown")) {
			switch (result) {
				case VK_NOT_READY -> returnCodeName = "VK_NOT_READY";
				case VK_TIMEOUT -> returnCodeName = "VK_TIMEOUT";
				case VK_EVENT_SET -> returnCodeName = "VK_EVENT_SET";
				case VK_EVENT_RESET -> returnCodeName = "VK_EVENT_RESET";
				case VK_INCOMPLETE -> returnCodeName = "VK_INCOMPLETE";
			}
		}

		String messageStart = functionName + " ";
		if (context != null) messageStart += "(" + context + ") ";
		return messageStart + "returned " + result + " (" + returnCodeName + ")";
	}

	public final String functionName;
	public final int result;
	public final String context;

	public VulkanFailureException(String functionName, int result, String context) {
		super(generateMessage(functionName, result, context));
		this.functionName = functionName;
		this.result = result;
		this.context = context;
	}
}
