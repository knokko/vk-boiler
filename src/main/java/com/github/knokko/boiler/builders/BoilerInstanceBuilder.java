package com.github.knokko.boiler.builders;

import com.github.knokko.boiler.exceptions.MissingVulkanExtensionException;
import com.github.knokko.boiler.exceptions.MissingVulkanLayerException;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static com.github.knokko.boiler.utilities.CollectionHelper.encodeStringSet;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.EXTLayerSettings.*;
import static org.lwjgl.vulkan.KHRPortabilityEnumeration.VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR;
import static org.lwjgl.vulkan.KHRPortabilityEnumeration.VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK10.*;

class BoilerInstanceBuilder {

	static VkInstance createInstance(BoilerBuilder builder, ExtraBuilder extra) {

		var supportedLayers = new HashSet<String>();
		try (var stack = stackPush()) {
			var pNumLayers = stack.callocInt(1);
			assertVkSuccess(vkEnumerateInstanceLayerProperties(
					pNumLayers, null
			), "EnumerateInstanceLayerProperties", "count");
			int numLayers = pNumLayers.get(0);

			var layerProperties = VkLayerProperties.calloc(numLayers, stack);
			assertVkSuccess(vkEnumerateInstanceLayerProperties(
					pNumLayers, layerProperties
			), "EnumerateInstanceLayerProperties", "layers");
			for (var layer : layerProperties) {
				supportedLayers.add(layer.layerNameString());
			}
		}

		for (String layer : builder.requiredVulkanLayers) {
			if (!supportedLayers.contains(layer)) throw new MissingVulkanLayerException(layer);
		}
		extra.layers.addAll(builder.requiredVulkanLayers);
		for (String layer : builder.desiredVulkanLayers) {
			if (supportedLayers.contains(layer)) extra.layers.add(layer);
		}

		var supportedExtensions = new HashSet<String>();
		try (var stack = stackPush()) {
			var extensionsLayers = new HashSet<>(extra.layers);
			extensionsLayers.add(null);

			for (String layerName : extensionsLayers) {
				var pNumLayers = stack.callocInt(1);
				var pLayerName = layerName != null ? stack.UTF8(layerName) : null;
				assertVkSuccess(vkEnumerateInstanceExtensionProperties(
						pLayerName, pNumLayers, null
				), "EnumerateInstanceExtensionProperties", "count");
				int numLayers = pNumLayers.get(0);

				var extensionProperties = VkExtensionProperties.calloc(numLayers, stack);
				assertVkSuccess(vkEnumerateInstanceExtensionProperties(
						pLayerName, pNumLayers, extensionProperties
				), "EnumerateInstanceExtensionProperties", "extensions");
				for (var extension : extensionProperties) {
					supportedExtensions.add(extension.extensionNameString());
				}
			}
		}

		for (String extension : builder.requiredVulkanInstanceExtensions) {
			if (!supportedExtensions.contains(extension))
				throw new MissingVulkanExtensionException("instance", extension);
		}
		extra.instanceExtensions.addAll(builder.requiredVulkanInstanceExtensions);
		for (String extension : builder.desiredVulkanInstanceExtensions) {
			if (supportedExtensions.contains(extension)) extra.instanceExtensions.add(extension);
		}

		VkInstance vkInstance;
		try (var stack = stackPush()) {
			var appInfo = VkApplicationInfo.calloc(stack);
			appInfo.sType$Default();
			appInfo.pApplicationName(stack.UTF8(builder.applicationName));
			appInfo.applicationVersion(builder.applicationVersion);
			appInfo.pEngineName(stack.UTF8(builder.engineName));
			appInfo.engineVersion(builder.engineVersion);
			appInfo.apiVersion(builder.apiVersion);

			var validationSettings = chooseValidationSettings(builder, stack);

			var ciInstance = VkInstanceCreateInfo.calloc(stack);
			ciInstance.sType$Default();
			if (validationSettings != null) ciInstance.pNext(validationSettings);
			if (extra.instanceExtensions.contains(VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME)) {
				ciInstance.flags(VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR);
			} else ciInstance.flags(0);
			ciInstance.pApplicationInfo(appInfo);
			ciInstance.ppEnabledLayerNames(encodeStringSet(extra.layers, stack));
			ciInstance.ppEnabledExtensionNames(encodeStringSet(extra.instanceExtensions, stack));

			for (var preCreator : builder.preInstanceCreators) {
				preCreator.beforeInstanceCreation(ciInstance, stack);
			}

			vkInstance = builder.vkInstanceCreator.vkCreateInstance(ciInstance, builder.allocationCallbacks, stack);
		}
		return vkInstance;
	}

	private static VkLayerSettingsCreateInfoEXT chooseValidationSettings(BoilerBuilder builder, MemoryStack stack) {
		if (builder.validationFeatures != null) {

			VkLayerSettingsCreateInfoEXT ciValidationSettings = VkLayerSettingsCreateInfoEXT.calloc(stack);
			ciValidationSettings.sType$Default();

			List<String> chosenLayerSettings = chooseValidationSettings(builder);
			if (chosenLayerSettings.isEmpty()) return null;

			var pTrue = stack.calloc(4).putInt(0, VK_TRUE);
			var vvl = stack.UTF8("VK_LAYER_KHRONOS_validation");
			var pValidationSettings = VkLayerSettingEXT.calloc(chosenLayerSettings.size(), stack);

			for (int index = 0; index < chosenLayerSettings.size(); index++) {
				var setting = pValidationSettings.get(index);
				setting.pLayerName(vvl);
				setting.pSettingName(stack.UTF8(chosenLayerSettings.get(index)));
				setting.type(VK_LAYER_SETTING_TYPE_BOOL32_EXT);
				setting.pValues(pTrue);
				VkLayerSettingEXT.nvalueCount(setting.address(), 1);
			}

			ciValidationSettings.pSettings(pValidationSettings);
			return ciValidationSettings;
		} else return null;
	}

	private static List<String> chooseValidationSettings(BoilerBuilder builder) {
		List<String> chosenLayerSettings = new ArrayList<>();

		if (builder.validationFeatures.debugPrint()) chosenLayerSettings.add("printf_enable");
		if (builder.validationFeatures.gpuAssisted()) {
			chosenLayerSettings.add("gpuav_enable");
			chosenLayerSettings.add("gpuav_safe_mode");
			chosenLayerSettings.add("gpuav_force_on_robustness");
		}
		if (builder.validationFeatures.bestPractices()) {
			chosenLayerSettings.add("validate_best_practices");
			chosenLayerSettings.add("validate_best_practices_arm");
			chosenLayerSettings.add("validate_best_practices_amd");
			chosenLayerSettings.add("validate_best_practices_nvidia");
			chosenLayerSettings.add("validate_best_practices_img");
		}
		if (builder.validationFeatures.synchronization()) chosenLayerSettings.add("validate_sync");
		return chosenLayerSettings;
	}
}
