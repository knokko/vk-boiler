package com.github.knokko.boiler.debug;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.memory.callbacks.CallbackUserData;
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

	/**
	 * Whether <i>VK_EXT_debug_utils</i> has been enabled
	 */
	public final boolean hasDebug;

	/**
	 * This constructor is meant for internal use only. You should use <i>boilerInstance.debug</i> instead.
	 */
	public BoilerDebug(BoilerInstance instance) {
		this.instance = instance;
		this.hasDebug = instance.instanceExtensions.contains(VK_EXT_DEBUG_UTILS_EXTENSION_NAME);
	}

	/**
	 * Assigns the given debug name (using <i>vkSetDebugUtilsObjectNameEXT</i>) to the given object, if
	 * <i>VK_EXT_debug_utils</i> is enabled (does nothing otherwise).
	 * @param stack The memory stack onto which the <i>VkDebugUtilsObjectNameInfoEXT</i> should be allocated
	 * @param object The object that should get a name
	 * @param type The <i>VkObjectType</i> of the given object
	 * @param name The debug name that should be assigned to the given object
	 */
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

	/**
	 * Creates a debug messenger when <i>VK_EXT_debug_utils</i> is enabled (returns <i>VK_NULL_HANDLE</i> otherwise).
	 * @param stack The memory stack onto which the <i>VkDebugUtilsMessengerCreateInfoEXT</i> should be allocated
	 * @param messageSeverityBits The <i>VkDebugUtilsMessageSeverityFlagBitsEXT</i>
	 * @param messageTypeBits The <i>VkDebugUtilsMessageTypeFlagBitsEXT</i>
	 * @param callback The callback function that should be called when a validation error/warning/info is encountered
	 * @param name The debug name of the debug messenger
	 * @return The handle of the created debug messenger, or <i>VK_NULL_HANDLE</i> when <i>VK_EXT_debug_utils</i> is
	 * not enabled
	 */
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
				instance.vkInstance(), ciMessenger, CallbackUserData.DEBUG_MESSENGER.put(stack, instance), pMessenger
		), "CreateDebugUtilsMessengerEXT", name);
		return pMessenger.get(0);
	}

	@FunctionalInterface
	public interface BoilerDebugCallback {

		void handleDebugMessage(VkDebugUtilsMessengerCallbackDataEXT data, int severity, int messageType);
	}
}
