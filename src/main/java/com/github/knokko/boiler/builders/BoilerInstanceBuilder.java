package com.github.knokko.boiler.builders;

import com.github.knokko.boiler.builders.instance.ValidationFeaturesChecker;
import com.github.knokko.boiler.exceptions.MissingVulkanExtensionException;
import com.github.knokko.boiler.exceptions.MissingVulkanLayerException;
import com.github.knokko.boiler.utilities.CollectionHelper;
import org.lwjgl.vulkan.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.EXTLayerSettings.VK_LAYER_SETTING_TYPE_BOOL32_EXT;
import static org.lwjgl.vulkan.EXTLayerSettings.VK_LAYER_SETTING_TYPE_INT32_EXT;
import static org.lwjgl.vulkan.KHRPortabilityEnumeration.VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR;
import static org.lwjgl.vulkan.KHRPortabilityEnumeration.VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK10.*;

class BoilerInstanceBuilder {

	static Result createInstance(BoilerBuilder builder) {

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
		var enabledLayers = new HashSet<>(builder.requiredVulkanLayers);
		for (String layer : builder.desiredVulkanLayers) {
			if (supportedLayers.contains(layer)) enabledLayers.add(layer);
		}

		var supportedExtensions = new HashSet<String>();
		try (var stack = stackPush()) {
			var extensionsLayers = new HashSet<>(enabledLayers);
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
		var enabledExtensions = new HashSet<>(builder.requiredVulkanInstanceExtensions);
		for (String extension : builder.desiredVulkanInstanceExtensions) {
			if (supportedExtensions.contains(extension)) enabledExtensions.add(extension);
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

			VkLayerSettingsCreateInfoEXT ciValidationSettings;
			if (builder.validationFeatures != null) {
				ciValidationSettings = VkLayerSettingsCreateInfoEXT.calloc(stack);
				ciValidationSettings.sType$Default();

				var supportedValidationFeatures = new ValidationFeaturesChecker(stack);
				List<String> chosenLayerSettings = new ArrayList<>();

				boolean gpuAssisted = builder.validationFeatures.gpuAssisted() && supportedValidationFeatures.gpuAssistedValidation;
				boolean debugPrint = builder.validationFeatures.debugPrint() && supportedValidationFeatures.debugPrintf;
				if (debugPrint) gpuAssisted = false;

				if (gpuAssisted) chosenLayerSettings.add("gpuav_debug_validate_instrumented_shaders");
				if (builder.validationFeatures.bestPractices()) chosenLayerSettings.add("validate_best_practices");
				if (builder.validationFeatures.synchronization()) chosenLayerSettings.add("validate_sync");
				if (builder.vendorBestPractices != null) {
					if (builder.vendorBestPractices.arm()) chosenLayerSettings.add("validate_best_practices_arm");
					if (builder.vendorBestPractices.amd()) chosenLayerSettings.add("validate_best_practices_amd");
					if (builder.vendorBestPractices.img()) chosenLayerSettings.add("validate_best_practices_img");
					if (builder.vendorBestPractices.nvidia()) chosenLayerSettings.add("validate_best_practices_nvidia");
				}

				supportedValidationFeatures.destroy();

				int numLayerSettings = chosenLayerSettings.size();
				if (gpuAssisted || debugPrint) numLayerSettings += 1;

				var pTrue = stack.calloc(4).putInt(0, VK_TRUE);

				var vvl = stack.UTF8("VK_LAYER_KHRONOS_validation");
				var pValidationSettings = VkLayerSettingEXT.calloc(numLayerSettings, stack);
				for (int index = 0; index < chosenLayerSettings.size(); index++) {
					var setting = pValidationSettings.get(index);
					setting.pLayerName(vvl);
					setting.pSettingName(stack.UTF8(chosenLayerSettings.get(index)));
					setting.type(VK_LAYER_SETTING_TYPE_BOOL32_EXT);
					setting.pValues(pTrue);
					VkLayerSettingEXT.nvalueCount(setting.address(), 1);
				}

				if (gpuAssisted || debugPrint) {
					var setting = pValidationSettings.get(chosenLayerSettings.size());
					setting.pLayerName(vvl);
					setting.pSettingName(stack.UTF8("validate_gpu_based"));
					setting.type(VK_LAYER_SETTING_TYPE_INT32_EXT);

					int value = debugPrint ? 1 : 2;
					setting.pValues(stack.calloc(4).putInt(0, value));
					VkLayerSettingEXT.nvalueCount(setting.address(), 1);
				}

				if (numLayerSettings > 0) ciValidationSettings.pSettings(pValidationSettings);
			} else ciValidationSettings = null;

			var ciInstance = VkInstanceCreateInfo.calloc(stack);
			ciInstance.sType$Default();
			if (ciValidationSettings != null) ciInstance.pNext(ciValidationSettings);
			if (enabledExtensions.contains(VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME)) {
				ciInstance.flags(VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR);
			} else ciInstance.flags(0);
			ciInstance.pApplicationInfo(appInfo);
			ciInstance.ppEnabledLayerNames(CollectionHelper.encodeStringSet(enabledLayers, stack));
			ciInstance.ppEnabledExtensionNames(CollectionHelper.encodeStringSet(enabledExtensions, stack));

			for (var preCreator : builder.preInstanceCreators) {
				preCreator.beforeInstanceCreation(ciInstance, stack);
			}

			vkInstance = builder.vkInstanceCreator.vkCreateInstance(ciInstance, stack);
		}
		return new Result(vkInstance, enabledLayers, enabledExtensions);
	}

	record Result(VkInstance vkInstance, Set<String> enabledLayers, Set<String> enabledExtensions) {
	}
}
