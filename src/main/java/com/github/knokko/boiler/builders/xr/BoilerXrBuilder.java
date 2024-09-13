package com.github.knokko.boiler.builders.xr;

import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.exceptions.MissingOpenXrExtensionException;
import com.github.knokko.boiler.exceptions.MissingOpenXrLayerException;
import com.github.knokko.boiler.exceptions.XrVersionConflictException;
import com.github.knokko.boiler.xr.XrBoiler;
import org.lwjgl.PointerBuffer;
import org.lwjgl.openxr.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static com.github.knokko.boiler.utilities.CollectionHelper.createSet;
import static com.github.knokko.boiler.exceptions.OpenXrFailureException.assertXrSuccess;
import static org.lwjgl.openxr.KHRVulkanEnable2.XR_KHR_VULKAN_ENABLE2_EXTENSION_NAME;
import static org.lwjgl.openxr.KHRVulkanEnable2.xrGetVulkanGraphicsRequirements2KHR;
import static org.lwjgl.openxr.XR10.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.VK_API_VERSION_MAJOR;
import static org.lwjgl.vulkan.VK10.VK_API_VERSION_MINOR;

public class BoilerXrBuilder {

	private final Set<String> desiredLayers = new HashSet<>();
	private final Set<String> requiredLayers = new HashSet<>();

	private final Set<String> desiredExtensions = new HashSet<>();
	private final Set<String> requiredExtensions = createSet(XR_KHR_VULKAN_ENABLE2_EXTENSION_NAME);

	private int formFactor = XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY;

	/**
	 * Enables the given OpenXR layers if and only if they are supported by the OpenXR runtime. The unsupported
	 * layers will be ignored.
	 */
	public BoilerXrBuilder desiredLayers(String... desiredLayers) {
		Collections.addAll(this.desiredLayers, desiredLayers);
		return this;
	}

	/**
	 * Enables the given OpenXR layers. If any of them is not supported by the OpenXR runtime, a
	 * <i>MissingOpenXrLayerException</i> will be thrown during the `build` method.
	 */
	public BoilerXrBuilder requiredLayers(String... requiredLayers) {
		Collections.addAll(this.requiredLayers, requiredLayers);
		return this;
	}

	/**
	 * Enables the given OpenXR extensions if and only if they are supported by the OpenXR runtime. The unsupported
	 * extensions will be ignored.
	 */
	public BoilerXrBuilder desiredExtensions(String... desiredExtensions) {
		Collections.addAll(this.desiredExtensions, desiredExtensions);
		return this;
	}

	/**
	 * Enables the given OpenXR extensions. If any of them is not supported by the OpenXR runtime, a
	 * <i>MissingOpenXrExtensionException</i> will be thrown during the `build` method.
	 */
	public BoilerXrBuilder requiredExtensions(String... requiredExtensions) {
		Collections.addAll(this.requiredExtensions, requiredExtensions);
		return this;
	}

	/**
	 * Changes the OpenXR form factor that will be used in <i>xrGetSystem</i>. The default value is
	 * <i>XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY</i>.
	 */
	public BoilerXrBuilder formFactor(int formFactor) {
		this.formFactor = formFactor;
		return this;
	}

	/**
	 * Note: this method is meant for internal use by `BoilerBuilder`. You should not need to call this method
	 * yourself.
	 */
	public XrBoiler build(
			BoilerBuilder builder, boolean enableValidation, boolean enableApiDump, int vkApiVersion,
			String appName, int appVersion,
			String engineName, int engineVersion
	) {
		if (enableValidation) this.requiredLayers.add("XR_APILAYER_LUNARG_core_validation");
		if (enableApiDump) this.requiredLayers.add("XR_APILAYER_LUNARG_api_dump");

		Set<String> supportedLayers;
		try (var stack = stackPush()) {
			var pNumLayers = stack.callocInt(1);
			assertXrSuccess(xrEnumerateApiLayerProperties(
					pNumLayers, null
			), "EnumerateApiLayerProperties", "count");
			int numLayers = pNumLayers.get(0);

			var pSupportedLayers = XrApiLayerProperties.calloc(numLayers, stack);
			for (int index = 0; index < numLayers; index++) {
				//noinspection resource
				pSupportedLayers.get(index).type$Default();
			}
			assertXrSuccess(xrEnumerateApiLayerProperties(
					pNumLayers, pSupportedLayers
			), "EnumerateApiLayerProperties", "layers");

			supportedLayers = new HashSet<>(numLayers);
			for (int index = 0; index < numLayers; index++) {
				supportedLayers.add(pSupportedLayers.get(index).layerNameString());
			}
		}

		for (String layer : requiredLayers) {
			if (!supportedLayers.contains(layer)) throw new MissingOpenXrLayerException(layer);
		}
		var enabledLayers = new HashSet<>(requiredLayers);
		for (String layer : desiredLayers) {
			if (supportedLayers.contains(layer)) enabledLayers.add(layer);
		}

		Set<String> supportedExtensions = new HashSet<>();

		Set<String> checkedLayers = new HashSet<>(enabledLayers);
		checkedLayers.add(null);

		for (String layer : checkedLayers) {
			try (var stack = stackPush()) {
				var pLayerName = layer == null ? null : stack.UTF8(layer);

				var pNumExtensions = stack.callocInt(1);
				assertXrSuccess(xrEnumerateInstanceExtensionProperties(
						pLayerName, pNumExtensions, null
				), "EnumerateInstanceExtensionProperties", "count " + layer);
				int numExtensions = pNumExtensions.get(0);

				var pSupportedExtensions = XrExtensionProperties.calloc(numExtensions, stack);
				for (int index = 0; index < numExtensions; index++) {
					//noinspection resource
					pSupportedExtensions.get(index).type$Default();
				}
				assertXrSuccess(xrEnumerateInstanceExtensionProperties(
						pLayerName, pNumExtensions, pSupportedExtensions
				), "EnumerateInstanceExtensionProperties", "extensions of " + layer);

				for (int index = 0; index < numExtensions; index++) {
					supportedExtensions.add(pSupportedExtensions.get(index).extensionNameString());
				}
			}
		}

		for (String extension : requiredExtensions) {
			if (!supportedExtensions.contains(extension)) throw new MissingOpenXrExtensionException(extension);
		}
		Set<String> enabledExtensions = new HashSet<>(requiredExtensions);
		for (String extension : desiredExtensions) {
			if (supportedExtensions.contains(extension)) enabledExtensions.add(extension);
		}

		XrInstance xrInstance;
		try (var stack = stackPush()) {
			var appInfo = XrApplicationInfo.calloc(stack);
			appInfo.applicationName(stack.UTF8(appName));
			appInfo.applicationVersion(appVersion);
			appInfo.engineName(stack.UTF8(engineName));
			appInfo.engineVersion(engineVersion);
			appInfo.apiVersion(XR_MAKE_VERSION(1, 0, 0));

			PointerBuffer pLayerNames = null;
			if (!enabledLayers.isEmpty()) {
				pLayerNames = stack.callocPointer(enabledLayers.size());
				for (String layer : enabledLayers) {
					pLayerNames.put(stack.UTF8(layer));
				}
				pLayerNames.flip();
			}

			PointerBuffer pExtensionNames = null;
			if (!enabledExtensions.isEmpty()) {
				pExtensionNames = stack.callocPointer(enabledExtensions.size());
				for (String extension : enabledExtensions) {
					pExtensionNames.put(stack.UTF8(extension));
				}
				pExtensionNames.flip();
			}

			var ciInstance = XrInstanceCreateInfo.calloc(stack);
			ciInstance.type$Default();
			ciInstance.createFlags(0);
			ciInstance.applicationInfo(appInfo);
			ciInstance.enabledApiLayerNames(pLayerNames);
			ciInstance.enabledExtensionNames(pExtensionNames);

			var pInstance = stack.callocPointer(1);

			assertXrSuccess(xrCreateInstance(
					ciInstance, pInstance
			), "CreateInstance", "BoilerXrBuilder");
			xrInstance = new XrInstance(pInstance.get(0), ciInstance);
		}

		long xrSystem;
		try (var stack = stackPush()) {
			var giSystem = XrSystemGetInfo.calloc(stack);
			giSystem.type$Default();
			giSystem.formFactor(formFactor);

			var pSystem = stack.callocLong(1);

			assertXrSuccess(xrGetSystem(xrInstance, giSystem, pSystem), "GetSystem", "BoilerXrBuilder");
			xrSystem = pSystem.get(0);

			var requirements = XrGraphicsRequirementsVulkan2KHR.calloc(stack);
			requirements.type$Default();

			assertXrSuccess(xrGetVulkanGraphicsRequirements2KHR(
					xrInstance, xrSystem, requirements
			), "GetVulkanGraphicsRequirements2KHR", "BoilerXrBuilder");

			int minMajorVersion = XR_VERSION_MAJOR(requirements.minApiVersionSupported());
			int minMinorVersion = XR_VERSION_MINOR(requirements.minApiVersionSupported());
			int maxMajorVersion = XR_VERSION_MAJOR(requirements.maxApiVersionSupported());
			int maxMinorVersion = XR_VERSION_MINOR(requirements.maxApiVersionSupported());
			int majorVersion = VK_API_VERSION_MAJOR(vkApiVersion);
			int minorVersion = VK_API_VERSION_MINOR(vkApiVersion);

			if (minMajorVersion > majorVersion || (minMajorVersion == majorVersion && minMinorVersion > minorVersion)) {
				throw new XrVersionConflictException(
						"The OpenXR runtime only supports Vulkan " + minMajorVersion + "." + minMinorVersion +
								" and later, but you requested Vulkan " + majorVersion + "." + minorVersion
				);
			}

			if (maxMajorVersion < majorVersion || (maxMajorVersion == majorVersion && maxMinorVersion < minorVersion)) {
				throw new XrVersionConflictException(
						"The OpenXR runtime only supports Vulkan " + maxMajorVersion + "." + maxMinorVersion +
								" and earlier, but you requested Vulkan " + majorVersion + "." + minorVersion
				);
			}
		}

		builder.vkInstanceCreator(new XrInstanceCreator(xrInstance, xrSystem));
		builder.physicalDeviceSelector(new XrDeviceSelector(xrInstance, xrSystem));
		builder.vkDeviceCreator(new XrDeviceCreator(xrInstance, xrSystem));

		return new XrBoiler(xrInstance, xrSystem);
	}
}
