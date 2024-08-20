package com.github.knokko.boiler.debug;

import com.github.knokko.boiler.instance.BoilerInstance;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDebugUtilsMessengerCallbackDataEXT;
import org.lwjgl.vulkan.VkDebugUtilsMessengerCreateInfoEXT;
import org.lwjgl.vulkan.VkDebugUtilsObjectNameInfoEXT;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.VK10.VK_FALSE;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;

public class BoilerDebug {

	private final BoilerInstance instance;
	public final boolean hasDebug;

	public BoilerDebug(BoilerInstance instance) {
		this.instance = instance;
		this.hasDebug = instance.instanceExtensions.contains(VK_EXT_DEBUG_UTILS_EXTENSION_NAME);
	}

	public void name(MemoryStack stack, long object, int type, String name) {
		if (hasDebug) {
			var nameInfo = VkDebugUtilsObjectNameInfoEXT.calloc(stack);
			nameInfo.sType$Default();
			nameInfo.objectType(type);
			nameInfo.objectHandle(object);
			nameInfo.pObjectName(stack.UTF8(name));

			assertVkSuccess(vkSetDebugUtilsObjectNameEXT(
					instance.vkDevice(), nameInfo
			), "SetDebugUtilsObjectNameEXT", name);
		}
	}

	public long createMessenger(
			MemoryStack stack, int messageSeverityBits, int messageTypeBits, BoilerDebugCallback callback, String name
	) {
		if (!hasDebug) return VK_NULL_HANDLE;

		var ciMessenger = VkDebugUtilsMessengerCreateInfoEXT.calloc(stack);
		ciMessenger.sType$Default();
		ciMessenger.messageSeverity(messageSeverityBits);
		ciMessenger.messageType(messageTypeBits);
		ciMessenger.pfnUserCallback((severity, type, data, userData) -> {
			try (var callbackData = VkDebugUtilsMessengerCallbackDataEXT.create(data)) {
				callback.handleDebugMessage(callbackData, severity, type);
			}
			return VK_FALSE;
		});

		var pMessenger = stack.callocLong(1);
		assertVkSuccess(vkCreateDebugUtilsMessengerEXT(
				instance.vkInstance(), ciMessenger, null, pMessenger
		), "CreateDebugUtilsMessengerEXT", name);
		return pMessenger.get(0);
	}

	@FunctionalInterface
	public interface BoilerDebugCallback {

		void handleDebugMessage(VkDebugUtilsMessengerCallbackDataEXT data, int severity, int messageType);
	}
}
