package com.github.knokko.boiler.builders;

import com.github.knokko.boiler.builders.instance.ValidationFeatures;
import com.github.knokko.boiler.builders.queue.QueueFamilyAllocation;
import com.github.knokko.boiler.builders.queue.QueueFamilyMapping;
import com.github.knokko.boiler.debug.ValidationException;
import com.github.knokko.boiler.exceptions.NoVkPhysicalDeviceException;
import com.github.knokko.boiler.utilities.CollectionHelper;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.*;

import java.util.HashSet;
import java.util.Objects;

import static com.github.knokko.boiler.utilities.CollectionHelper.createSet;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memUTF8;
import static org.lwjgl.vulkan.EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME;
import static org.lwjgl.vulkan.EXTLayerSettings.VK_EXT_LAYER_SETTINGS_EXTENSION_NAME;
import static org.lwjgl.vulkan.EXTLayerSettings.VK_STRUCTURE_TYPE_LAYER_SETTINGS_CREATE_INFO_EXT;
import static org.lwjgl.vulkan.KHRDynamicRendering.VK_KHR_DYNAMIC_RENDERING_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRGetSurfaceCapabilities2.VK_KHR_GET_SURFACE_CAPABILITIES_2_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRMaintenance1.VK_KHR_MAINTENANCE_1_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRPortabilityEnumeration.VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR;
import static org.lwjgl.vulkan.KHRPortabilityEnumeration.VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRSurface.VK_KHR_SURFACE_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK10.VK_API_VERSION_1_0;
import static org.lwjgl.vulkan.VK10.VK_MAKE_VERSION;
import static org.lwjgl.vulkan.VK12.*;
import static org.lwjgl.vulkan.VK13.VK_API_VERSION_1_3;
import static org.lwjgl.vulkan.VK13.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_3_FEATURES;
import static org.lwjgl.vulkan.VK14.VK_API_VERSION_1_4;
import static org.lwjgl.vulkan.VK14.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_4_FEATURES;

public class TestBoilerBuilder {

	@Test
	public void testSimpleInstanceBuilder() {
		boolean[] pDidCallInstanceCreator = {false};
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_0,
				"TestSimpleVulkan1.0",
				VK_MAKE_VERSION(1, 0, 0)
		).vkInstanceCreator((ciInstance, callbacks, stack) -> {
			assertEquals(0L, ciInstance.pNext());

			var appInfo = Objects.requireNonNull(ciInstance.pApplicationInfo());
			assertEquals("TestSimpleVulkan1.0", appInfo.pApplicationNameString());
			assertEquals(VK_API_VERSION_1_0, appInfo.apiVersion());
			pDidCallInstanceCreator[0] = true;
			return BoilerBuilder.DEFAULT_VK_INSTANCE_CREATOR.vkCreateInstance(ciInstance, callbacks, stack);
		}).build();

		assertTrue(pDidCallInstanceCreator[0]);
		assertThrows(UnsupportedOperationException.class, instance::window);

		instance.destroyInitialObjects();
	}

	@Test
	public void testComplexInstanceBuilder() {
		try {
			// SwiftShader doesn't support VK 1.4, which is all we have in GitHub Actions
			testComplexInstanceBuilder(VK_API_VERSION_1_4);
		} catch (NoVkPhysicalDeviceException notSupported) {
			testComplexInstanceBuilder(VK_API_VERSION_1_3);
		}
	}

	@SuppressWarnings("resource")
	private void testComplexInstanceBuilder(int apiVersion) {
		boolean[] pDidCallInstanceCreator = {false};
		boolean[] pDidCallDeviceCreator = {false};

		var builder = new BoilerBuilder(
				apiVersion, "TestComplexVulkan1.4", VK_MAKE_VERSION(1, 1, 1)
		)
				.engine("TestEngine", VK_MAKE_VERSION(0, 8, 4))
				.featurePicker12((stack, supported, toEnable) -> {
					// This feature has 100% coverage according to Vulkan hardware database
					assertTrue(supported.imagelessFramebuffer());
					toEnable.imagelessFramebuffer(true);
				})
				.featurePicker13((stack, supported, toEnable) -> {
					// Dynamic rendering is required in VK1.3
					assertTrue(supported.dynamicRendering());
					toEnable.dynamicRendering(true);
				})
				.requiredVkInstanceExtensions(VK_KHR_GET_SURFACE_CAPABILITIES_2_EXTENSION_NAME)
				.desiredVkInstanceExtensions(VK_KHR_SURFACE_EXTENSION_NAME)
				.validation(new ValidationFeatures(
						true, false, false, true
				))
				.vkInstanceCreator((ciInstance, callbacks, stack) -> {
					pDidCallInstanceCreator[0] = true;

					assertNotEquals(0L, ciInstance.pNext());
					var validationSettings = VkLayerSettingsCreateInfoEXT.create(ciInstance.pNext());
					assertEquals(VK_STRUCTURE_TYPE_LAYER_SETTINGS_CREATE_INFO_EXT, validationSettings.sType());
					assertEquals(4, validationSettings.settingCount());

					var settings = validationSettings.pSettings();
					boolean enabledGpuValidation = false;
					assertNotNull(settings);
					while (settings.hasRemaining()) {
						var setting = settings.get();
						assertEquals("VK_LAYER_KHRONOS_validation", setting.pLayerNameString());
						if (setting.pSettingNameString().equals("gpuav_enable")) {
							enabledGpuValidation = true;
						}
						assertNotEquals("validate_best_practices", setting.pSettingNameString());
						assertEquals(1, setting.valueCount());
						assertEquals(1, Objects.requireNonNull(setting.pValues(4)).getInt());
					}

					assertTrue(enabledGpuValidation);

					var appInfo = Objects.requireNonNull(ciInstance.pApplicationInfo());
					assertEquals("TestComplexVulkan1.4", appInfo.pApplicationNameString());
					assertEquals(apiVersion, appInfo.apiVersion());
					assertEquals("TestEngine", appInfo.pEngineNameString());
					assertEquals(VK_MAKE_VERSION(0, 8, 4), appInfo.engineVersion());

					assertEquals(1, ciInstance.enabledLayerCount());
					assertEquals(
							"VK_LAYER_KHRONOS_validation",
							memUTF8(Objects.requireNonNull(ciInstance.ppEnabledLayerNames()).get(0))
					);

					var pExtensions = Objects.requireNonNull(ciInstance.ppEnabledExtensionNames());
					var actualExtensions = new HashSet<String>(ciInstance.enabledExtensionCount());
					for (int index = 0; index < pExtensions.limit(); index++) {
						actualExtensions.add(memUTF8(pExtensions.get(index)));
					}

					var expectedExtensions = createSet(
							VK_EXT_LAYER_SETTINGS_EXTENSION_NAME,
							VK_EXT_DEBUG_UTILS_EXTENSION_NAME,
							VK_KHR_GET_SURFACE_CAPABILITIES_2_EXTENSION_NAME,
							VK_KHR_SURFACE_EXTENSION_NAME
					);

					// Portability enumeration is optional, and depends on the current graphics driver
					if (ciInstance.flags() == VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR) {
						expectedExtensions.add(VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME);
					}

					assertEquals(expectedExtensions, actualExtensions);

					return BoilerBuilder.DEFAULT_VK_INSTANCE_CREATOR.vkCreateInstance(ciInstance, callbacks, stack);
				})
				.vkDeviceCreator((
						ciDevice, instanceExtensions, physicalDevice,
						callbacks, stack
				) -> {
					VkPhysicalDeviceVulkan11Features enabledFeatures11 = null;
					VkPhysicalDeviceVulkan12Features enabledFeatures12 = null;
					VkPhysicalDeviceVulkan13Features enabledFeatures13 = null;
					VkPhysicalDeviceVulkan14Features enabledFeatures14 = null;

					var nextStruct = VkBaseInStructure.createSafe(ciDevice.pNext());
					while (nextStruct != null) {
						if (nextStruct.sType() == VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_1_FEATURES) {
							enabledFeatures11 = VkPhysicalDeviceVulkan11Features.create(nextStruct.address());
						}
						if (nextStruct.sType() == VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES) {
							enabledFeatures12 = VkPhysicalDeviceVulkan12Features.create(nextStruct.address());
						}
						if (nextStruct.sType() == VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_3_FEATURES) {
							enabledFeatures13 = VkPhysicalDeviceVulkan13Features.create(nextStruct.address());
						}
						if (nextStruct.sType() == VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_4_FEATURES) {
							enabledFeatures14 = VkPhysicalDeviceVulkan14Features.create(nextStruct.address());
						}
						nextStruct = nextStruct.pNext();
					}

					assertNull(enabledFeatures11);
					assertNotNull(enabledFeatures12);
					assertTrue(enabledFeatures12.imagelessFramebuffer());
					assertNotNull(enabledFeatures13);
					assertTrue(enabledFeatures13.dynamicRendering());
					if (apiVersion == VK_API_VERSION_1_4) {
						assertNotNull(enabledFeatures14);
						assertTrue(enabledFeatures14.indexTypeUint8());
					}

					pDidCallDeviceCreator[0] = true;
					return BoilerBuilder.DEFAULT_VK_DEVICE_CREATOR.vkCreateDevice(
							ciDevice, instanceExtensions, physicalDevice, callbacks, stack
					);
				})
				.forbidValidationErrors();
		if (apiVersion == VK_API_VERSION_1_4) {
			builder.featurePicker14((stack, supported, toEnable) -> {
				// This feature has almost 100% coverage according to Vulkan hardware database
				assertTrue(supported.indexTypeUint8());
				toEnable.indexTypeUint8(true);
			});
		}
		var instance = builder.build();

		instance.destroyInitialObjects();
		assertTrue(pDidCallInstanceCreator[0]);
		assertTrue(pDidCallDeviceCreator[0]);
	}

	private void testApiVersionCheck(int apiVersion) {
		assertThrows(UnsupportedOperationException.class, () -> new BoilerBuilder(
				apiVersion, "TestApiVersionCheck", 2
		));
	}

	@Test
	public void testApiVersionCheck() {
		testApiVersionCheck(VK_MAKE_API_VERSION(0, 1, 50, 0));
		testApiVersionCheck(VK_MAKE_API_VERSION(0, 2, 0, 0));
	}

	private void testRequiredFeatures10(int apiVersion) {
		var builder = new BoilerBuilder(apiVersion, "TestRequiredFeatures", 1)
				.featurePicker10((stack, supported, enable) -> fail())
				.requiredFeatures10("robust buffer access", supportedFeatures -> {
					assertTrue(supportedFeatures.robustBufferAccess());
					return false;
				});
		assertThrows(NoVkPhysicalDeviceException.class, builder::build);
	}

	private void testRequiredFeatures11(int apiVersion) {
		var builder = new BoilerBuilder(apiVersion, "TestRequiredFeatures", 1)
				.featurePicker11((stack, supported, enable) -> fail())
				.requiredFeatures11("multiview", supportedFeatures -> {
					assertTrue(supportedFeatures.multiview());
					return false;
				});
		assertThrows(NoVkPhysicalDeviceException.class, builder::build);
	}

	private void testRequiredFeatures12(int apiVersion) {
		var builder = new BoilerBuilder(apiVersion, "TestRequiredFeatures", 1)
				.featurePicker12((stack, supported, enable) -> fail())
				.requiredFeatures12("hostQueryReset", supportedFeatures -> {
					assertTrue(supportedFeatures.hostQueryReset());
					return false;
				});
		assertThrows(NoVkPhysicalDeviceException.class, builder::build);
	}

	private void testRequiredFeatures13(int apiVersion) {
		var builder = new BoilerBuilder(apiVersion, "TestRequiredFeatures", 1)
				.featurePicker13((stack, supported, enable) -> fail())
				.requiredFeatures13("dynamic rendering", supportedFeatures -> {
					assertTrue(supportedFeatures.dynamicRendering());
					return false;
				});
		assertThrows(NoVkPhysicalDeviceException.class, builder::build);
	}

	private void testRequiredFeatures14(int apiVersion) {
		var builder = new BoilerBuilder(apiVersion, "TestRequiredFeatures", 1)
				.featurePicker14((stack, supported, enable) -> fail())
				.requiredFeatures14("indexTypeUint8", supportedFeatures -> {
					assertTrue(supportedFeatures.indexTypeUint8());
					return false;
				});
		assertThrows(NoVkPhysicalDeviceException.class, builder::build);
	}

	@Test
	public void testRequiredFeatures10() {
		int[] versions = {VK_API_VERSION_1_0, VK_API_VERSION_1_1, VK_API_VERSION_1_2, VK_API_VERSION_1_3};
		for (int version : versions) testRequiredFeatures10(version);
	}

	@Test
	public void testRequiredFeatures11() {
		int[] versions = {VK_API_VERSION_1_1, VK_API_VERSION_1_2, VK_API_VERSION_1_3, VK_API_VERSION_1_4};
		for (int version : versions) testRequiredFeatures11(version);
	}

	@Test
	public void testRequiredFeatures12() {
		testRequiredFeatures12(VK_API_VERSION_1_2);
		testRequiredFeatures12(VK_API_VERSION_1_3);
		testRequiredFeatures12(VK_API_VERSION_1_4);
	}

	@Test
	public void testRequiredFeatures13() {
		testRequiredFeatures13(VK_API_VERSION_1_3);
		testRequiredFeatures13(VK_API_VERSION_1_4);
	}

	@Test
	public void testRequiredFeatures14() {
		testRequiredFeatures14(VK_API_VERSION_1_4);
	}

	@Test
	public void testPreInstanceCreators() {
		class TestException extends RuntimeException {
		}

		boolean[] didCall = {false, false};

		assertThrows(TestException.class, () ->
				new BoilerBuilder(VK_API_VERSION_1_0, "TestPreInstance", 1)
						.beforeInstanceCreation((ciInstance, stack) -> didCall[0] = true)
						.beforeInstanceCreation((ciInstance, stack) -> didCall[1] = true)
						.vkInstanceCreator((ciInstance, callbacks, stack) -> {
							assertTrue(didCall[0]);
							assertTrue(didCall[1]);
							throw new TestException();
						}).build()
		);
	}

	@Test
	public void testPreDeviceCreators() {
		class TestException extends RuntimeException {
		}

		boolean[] didCall = {false, false};

		assertThrows(TestException.class, () ->
				new BoilerBuilder(VK_API_VERSION_1_0, "TestPreDevice", 1)
						.beforeDeviceCreation((ciDevice, instanceExtensions, physicalDevice, stack) -> didCall[0] = true)
						.beforeDeviceCreation((ciDevice, instanceExtensions, physicalDevice, stack) -> didCall[1] = true)
						.vkDeviceCreator((
								ciDevice, instanceExtensions, physicalDevice,
								callbacks, stack
						) -> {
							assertTrue(didCall[0]);
							assertTrue(didCall[1]);
							throw new TestException();
						}).build()
		);
	}

	@Test
	public void testExtraDeviceRequirements() {
		assertThrows(NoVkPhysicalDeviceException.class, () ->
				new BoilerBuilder(VK_API_VERSION_1_0, "TestExtraDeviceRequirements", 1)
						.extraDeviceRequirements("false", (device, windowSurface, stack) -> false)
						.build()
		);
		assertThrows(NoVkPhysicalDeviceException.class, () ->
				new BoilerBuilder(VK_API_VERSION_1_0, "TestExtraDeviceRequirements", 1)
						.extraDeviceRequirements("false", (device, windowSurface, stack) -> false)
						.extraDeviceRequirements("true", (device, windowSurface, stack) -> true)
						.build()
		);
		new BoilerBuilder(VK_API_VERSION_1_0, "TestExtraDeviceRequirements", 1)
				.extraDeviceRequirements("true", (device, windowSurface, stack) -> true)
				.extraDeviceRequirements("true", (device, windowSurface, stack) -> true)
				.build().destroyInitialObjects();
	}

	@Test
	public void testForbidValidationErrorsDuringDeviceCreation() {
		var builder = new BoilerBuilder(
				VK_API_VERSION_1_0, "TestForbidValidationErrorsDuringDeviceCreation", 1
		).validation().forbidValidationErrors().requiredDeviceExtensions(VK_KHR_DYNAMIC_RENDERING_EXTENSION_NAME);

		String message = assertThrows(ValidationException.class, builder::build).getMessage();
		assertTrue(message.contains("VUID-vkCreateDevice-ppEnabledExtensionNames"), "Message was " + message);
	}

	@Test
	public void testForbidValidationErrors() {
		var instance = new BoilerBuilder(VK_API_VERSION_1_0, "TestForbidValidationErrors", 1)
				.validation()
				.forbidValidationErrors().build();

		try (var stack = stackPush()) {
			var ciFence = VkFenceCreateInfo.calloc(stack);
			// Intentionally leave ciFence.sType 0, which should cause a validation error
			assertThrows(
					ValidationException.class,
					() -> vkCreateFence(instance.vkDevice(), ciFence, null, stack.callocLong(1))
			);
		}

		instance.destroyInitialObjects();
	}

	@Test
	public void testDesiredLayers() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_0, "TestDesiredLayers", 1
		).desiredVkLayers("bullshit", "VK_LAYER_KHRONOS_validation").build();

		assertEquals(createSet("VK_LAYER_KHRONOS_validation"), instance.explicitLayers);

		instance.destroyInitialObjects();
	}

	@Test
	public void testDesiredDeviceExtensions() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_0, "TestDesiredDeviceExtensions", 1
		)
				.desiredVkDeviceExtensions(createSet("bullshit", VK_KHR_MAINTENANCE_1_EXTENSION_NAME))
				.validation().forbidValidationErrors().build();

		assertTrue(instance.deviceExtensions.contains(VK_KHR_MAINTENANCE_1_EXTENSION_NAME));
		assertFalse(instance.deviceExtensions.contains("bullshit"));

		instance.destroyInitialObjects();
	}

	@Test
	public void testQueueFamilyMapper() {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_3, "TestQueueFamilyMapper", 1
		).validation().forbidValidationErrors().queueFamilyMapper((queueFamilies, deviceExtensions, presentSupport) -> {
			QueueFamilyAllocation allocations = new QueueFamilyAllocation(0, new float[]{0.25f});
			return new QueueFamilyMapping(allocations, allocations, allocations, allocations, allocations, new int[0]);
		}).build();

		// This test is somewhat crappy because I can't assume that multiple queues or queue families are supported...
		assertEquals(0, instance.queueFamilies().graphics().index());
		assertEquals(1, instance.queueFamilies().graphics().queues().size());
		assertEquals(0, instance.queueFamilies().videoEncode().index());
		assertEquals(1, instance.queueFamilies().videoEncode().queues().size());

		instance.destroyInitialObjects();
	}

	@Test
	public void testApiDump() {
		var builder = new BoilerBuilder(
				VK_API_VERSION_1_0, "TestApiDump", 1
		).apiDump().vkInstanceCreator((ciInstance, callbacks, stack) -> {
			var layers = CollectionHelper.decodeStringSet(ciInstance.ppEnabledLayerNames());
			assertTrue(layers.contains("VK_LAYER_LUNARG_api_dump"), "layers were " + layers);
			throw new RuntimeException("Mission completed");
		}).validation().forbidValidationErrors();

		String message = assertThrows(RuntimeException.class, builder::build).getMessage();
		if (!message.equals("Vulkan layer \"VK_LAYER_LUNARG_api_dump\" is required, but not supported")) {
			assertEquals("Mission completed", message);
		}
	}
}
