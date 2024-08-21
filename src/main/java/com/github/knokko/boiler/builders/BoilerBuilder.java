package com.github.knokko.boiler.builders;

import com.github.knokko.boiler.builders.device.*;
import com.github.knokko.boiler.builders.instance.PreVkInstanceCreator;
import com.github.knokko.boiler.builders.instance.ValidationFeatures;
import com.github.knokko.boiler.builders.instance.VkInstanceCreator;
import com.github.knokko.boiler.builders.queue.MinimalQueueFamilyMapper;
import com.github.knokko.boiler.builders.queue.QueueFamilyMapper;
import com.github.knokko.boiler.builders.xr.BoilerXrBuilder;
import com.github.knokko.boiler.debug.ValidationException;
import com.github.knokko.boiler.exceptions.*;
import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.xr.XrBoiler;
import org.lwjgl.vulkan.*;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static com.github.knokko.boiler.utilities.CollectionHelper.decodeStringSet;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memUTF8;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.EXTMemoryBudget.VK_EXT_MEMORY_BUDGET_EXTENSION_NAME;
import static org.lwjgl.vulkan.EXTSurfaceMaintenance1.VK_EXT_SURFACE_MAINTENANCE_1_EXTENSION_NAME;
import static org.lwjgl.vulkan.EXTSwapchainMaintenance1.VK_EXT_SWAPCHAIN_MAINTENANCE_1_EXTENSION_NAME;
import static org.lwjgl.vulkan.EXTValidationFeatures.*;
import static org.lwjgl.vulkan.KHRBindMemory2.VK_KHR_BIND_MEMORY_2_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRCreateRenderpass2.VK_KHR_CREATE_RENDERPASS_2_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRDedicatedAllocation.VK_KHR_DEDICATED_ALLOCATION_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRDepthStencilResolve.VK_KHR_DEPTH_STENCIL_RESOLVE_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRDynamicRendering.VK_KHR_DYNAMIC_RENDERING_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRGetMemoryRequirements2.VK_KHR_GET_MEMORY_REQUIREMENTS_2_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRGetPhysicalDeviceProperties2.VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRGetPhysicalDeviceProperties2.vkGetPhysicalDeviceFeatures2KHR;
import static org.lwjgl.vulkan.KHRGetSurfaceCapabilities2.VK_KHR_GET_SURFACE_CAPABILITIES_2_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRMaintenance2.VK_KHR_MAINTENANCE_2_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRMultiview.VK_KHR_MULTIVIEW_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRPortabilityEnumeration.VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.VK_API_VERSION_1_1;
import static org.lwjgl.vulkan.VK11.vkGetPhysicalDeviceFeatures2;
import static org.lwjgl.vulkan.VK12.VK_API_VERSION_1_2;
import static org.lwjgl.vulkan.VK13.VK_API_VERSION_1_3;

public class BoilerBuilder {

	public static final VkInstanceCreator DEFAULT_VK_INSTANCE_CREATOR = (ciInstance, stack) -> {
		var pInstance = stack.callocPointer(1);
		assertVkSuccess(vkCreateInstance(ciInstance, null, pInstance), "CreateInstance", "BoilerBuilder");
		return new VkInstance(pInstance.get(0), ciInstance);
	};

	public static final VkDeviceCreator DEFAULT_VK_DEVICE_CREATOR = (ciDevice, instanceExtensions, physicalDevice, stack) -> {
		var pDevice = stack.callocPointer(1);
		assertVkSuccess(vkCreateDevice(physicalDevice, ciDevice, null, pDevice), "CreateDevice", "BoilerBuilder");
		return new VkDevice(pDevice.get(0), physicalDevice, ciDevice);
	};

	final int apiVersion;
	final String applicationName;
	final int applicationVersion;

	long defaultTimeout = 1_000_000_000L;

	List<WindowBuilder> windows = new ArrayList<>();
	boolean initGLFW = true;

	BoilerXrBuilder xrBuilder;

	String engineName = "VkBoiler";
	int engineVersion = 4;

	final Set<String> desiredVulkanLayers = new HashSet<>();
	final Set<String> requiredVulkanLayers = new HashSet<>();

	final Set<String> desiredVulkanInstanceExtensions = new HashSet<>();
	final Set<String> requiredVulkanInstanceExtensions = new HashSet<>();

	final Set<String> desiredVulkanDeviceExtensions = new HashSet<>();
	final Set<String> requiredVulkanDeviceExtensions = new HashSet<>();

	ValidationFeatures validationFeatures = null;
	boolean forbidValidationErrors = false;

	boolean dynamicRendering = false;

	VkInstanceCreator vkInstanceCreator = DEFAULT_VK_INSTANCE_CREATOR;
	Collection<PreVkInstanceCreator> preInstanceCreators = new ArrayList<>();
	PhysicalDeviceSelector deviceSelector = new SimpleDeviceSelector(
			VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU,
			VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU
	);
	VkDeviceCreator vkDeviceCreator = DEFAULT_VK_DEVICE_CREATOR;
	Collection<PreVkDeviceCreator> preDeviceCreators = new ArrayList<>();

	Collection<FeaturePicker10> vkDeviceFeaturePicker10 = new ArrayList<>();
	Collection<FeaturePicker11> vkDeviceFeaturePicker11 = new ArrayList<>();
	Collection<FeaturePicker12> vkDeviceFeaturePicker12 = new ArrayList<>();
	Collection<FeaturePicker13> vkDeviceFeaturePicker13 = new ArrayList<>();

	Collection<Predicate<VkPhysicalDeviceFeatures>> vkRequiredFeatures10 = new ArrayList<>();
	Collection<Predicate<VkPhysicalDeviceVulkan11Features>> vkRequiredFeatures11 = new ArrayList<>();
	Collection<Predicate<VkPhysicalDeviceVulkan12Features>> vkRequiredFeatures12 = new ArrayList<>();
	Collection<Predicate<VkPhysicalDeviceVulkan13Features>> vkRequiredFeatures13 = new ArrayList<>();

	Collection<ExtraDeviceRequirements> extraDeviceRequirements = new ArrayList<>();

	boolean printDeviceRejectionInfo = false;

	QueueFamilyMapper queueFamilyMapper = new MinimalQueueFamilyMapper();

	private boolean didBuild = false;

	/**
	 * @param apiVersion The Vulkan api version that you want to use, for instance <i>VK_API_VERSION_1_0</i>.<br>
	 *                   All physical devices that don't support this api version will be ignored during device
	 *                   selection.<br>
	 *                   If not a single physical device on the target machine supports this api version,
	 *                   the boiler instance creation will fail with a <i>NoVkPhysicalDeviceException</i>.
	 * @param applicationName The 'name' of your application, which will be propagated to the VkApplicationInfo
	 * @param applicationVersion The version of your application, which will be propagated to the VkApplicationInfo
	 */
	public BoilerBuilder(int apiVersion, String applicationName, int applicationVersion) {
		if (VK_API_VERSION_PATCH(apiVersion) != 0) throw new IllegalArgumentException("Patch of API version must be 0");
		if (VK_API_VERSION_VARIANT(apiVersion) != 0)
			throw new IllegalArgumentException("Variant of API version must be 0");

		this.apiVersion = apiVersion;
		this.applicationName = applicationName;
		this.applicationVersion = applicationVersion;

		this.desiredVulkanInstanceExtensions.add(VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// Instance creation properties
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Enables the validation layer the given features.
	 * A <i>MissingVulkanLayerException</i> will be thrown if the validation layer is not supported.
	 */
	public BoilerBuilder validation(ValidationFeatures validationFeatures) {
		this.validationFeatures = validationFeatures;
		return this;
	}

	/**
	 * Enables the validation layer with basic validation, synchronization validation, and best practices. If the
	 * API version is at least 1.1, GPU-assisted validation will also be enabled.<br>
	 * A <i>MissingVulkanLayerException</i> will be thrown if the validation layer is not supported.
	 */
	public BoilerBuilder validation() {
		if (this.apiVersion == VK_API_VERSION_1_0) {
			return this.validation(new ValidationFeatures(
					false, false, false, true, true
			));
		} else {
			return this.validation(new ValidationFeatures(
					true, true, false, true, true
			));
		}
	}

	/**
	 * Ensures that a <i>ValidationException</i> will be thrown whenever a validation error is encountered, which is
	 * nice for tracing the function call that caused it, as well as unit testing.<br>
	 * This requires you to chain `.validation()`.
	 */
	public BoilerBuilder forbidValidationErrors() {
		this.forbidValidationErrors = true;
		return this;
	}

	/**
	 * Enables the given Vulkan (instance) layers if and only if they are supported by the target machine.
	 * The unsupported layers will be ignored.
	 */
	public BoilerBuilder desiredVkLayers(String... desiredLayers) {
		Collections.addAll(desiredVulkanLayers, desiredLayers);
		return this;
	}

	/**
	 * Enables the given Vulkan (instance) layers. If at least 1 of them is not supported by the target machine,
	 * a <i>MissingVulkanLayerException</i> will be thrown during the <i>build</i> method.
	 */
	public BoilerBuilder requiredVkLayers(String... requiredLayers) {
		Collections.addAll(requiredVulkanLayers, requiredLayers);
		return this;
	}

	/**
	 * Adds "VK_LAYER_LUNARG_api_dump" to the required layers.
	 */
	public BoilerBuilder apiDump() {
		return requiredVkLayers("VK_LAYER_LUNARG_api_dump");
	}

	/**
	 * Enables the given instance extensions, if and only if they are supported by the target machine. All
	 * unsupported extensions are ignored.
	 */
	public BoilerBuilder desiredVkInstanceExtensions(String... instanceExtensions) {
		Collections.addAll(desiredVulkanInstanceExtensions, instanceExtensions);
		return this;
	}

	/**
	 * Enables the given instance extensions. If at least 1 of them is not supported by the target machine, a
	 * <i>MissingVulkanExtensionException</i> will be thrown.
	 */
	public BoilerBuilder requiredVkInstanceExtensions(String... instanceExtensions) {
		Collections.addAll(requiredVulkanInstanceExtensions, instanceExtensions);
		return this;
	}

	/**
	 * Sets the engine name and engine version, which will be propagated to the <i>VkApplicationInfo</i>. By default,
	 * this will be "VkBoiler" version 4.
	 */
	public BoilerBuilder engine(String engineName, int engineVersion) {
		this.engineName = engineName;
		this.engineVersion = engineVersion;
		return this;
	}

	/**
	 * Adds a callback that will be called before <i>vkCreateInstance</i>, which can be used to change the
	 * <i>VkInstanceCreateInfo</i>.
	 */
	public BoilerBuilder beforeInstanceCreation(PreVkInstanceCreator preCreator) {
		this.preInstanceCreators.add(preCreator);
		return this;
	}

	/**
	 * Sets a custom <i>VkInstanceCreator</i>, which is a callback that should create the <i>VkInstance</i>, given the
	 * <i>VkInstanceCreateInfo</i> and a memory stack. The default instance creator <i>DEFAULT_VK_INSTANCE_CREATOR</i>
	 * will simply call <i>vkCreateInstance</i>, but you can override this if you need to do something special.<br>
	 * If you simply need to modify the <i>VkInstanceCreateInfo</i>, you should use <i>beforeInstanceCreation</i> instead.
	 */
	public BoilerBuilder vkInstanceCreator(VkInstanceCreator creator) {
		if (this.vkInstanceCreator != DEFAULT_VK_INSTANCE_CREATOR) {
			throw new IllegalStateException("Attempted to set multiple instance creators");
		}
		this.vkInstanceCreator = creator;
		return this;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// Physical device selection
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * During device selection, there are many reasons why physical device could be filtered out. If all physical
	 * devices are filtered out (or the target computer doesn't have any device that supports Vulkan), a
	 * <i>NoVkPhysicalDeviceException</i> will be thrown.<br>
	 *
	 * If you chain this method, the builder will tell you (via standard output) <b>why</b> it filtered out each device.
	 */
	public BoilerBuilder printDeviceRejectionInfo() {
		this.printDeviceRejectionInfo = true;
		return this;
	}

	/**
	 * Enables the following device extensions. Any device that doesn't support them will be filtered out during
	 * device selection.
	 */
	public BoilerBuilder requiredDeviceExtensions(String... deviceExtensions) {
		Collections.addAll(requiredVulkanDeviceExtensions, deviceExtensions);
		return this;
	}

	/**
	 * Adds a required features callback to the device selection procedure. When the features of a device fail the
	 * predicate, the corresponding device will be filtered out.
	 */
	public BoilerBuilder requiredFeatures10(Predicate<VkPhysicalDeviceFeatures> requiredFeatures) {
		this.vkRequiredFeatures10.add(requiredFeatures);
		return this;
	}

	/**
	 * Adds a required VK 1.1 features callback to the device selection procedure. When the features of a device fail
	 * the predicate, the corresponding device will be filtered out.
	 */
	public BoilerBuilder requiredFeatures11(Predicate<VkPhysicalDeviceVulkan11Features> requiredFeatures) {
		checkApiVersion(VK_API_VERSION_1_1);
		this.vkRequiredFeatures11.add(requiredFeatures);
		return this;
	}

	/**
	 * Adds a required VK 1.2 features callback to the device selection procedure. When the features of a device fail
	 * the predicate, the corresponding device will be filtered out.
	 */
	public BoilerBuilder requiredFeatures12(Predicate<VkPhysicalDeviceVulkan12Features> requiredFeatures) {
		checkApiVersion(VK_API_VERSION_1_2);
		this.vkRequiredFeatures12.add(requiredFeatures);
		return this;
	}

	/**
	 * Adds a required VK 1.3 features callback to the device selection procedure. When the features of a device fail
	 * the predicate, the corresponding device will be filtered out.
	 */
	public BoilerBuilder requiredFeatures13(Predicate<VkPhysicalDeviceVulkan13Features> requiredFeatures) {
		checkApiVersion(VK_API_VERSION_1_3);
		this.vkRequiredFeatures13.add(requiredFeatures);
		return this;
	}

	/**
	 * Adds custom device requirements to the device selection procedure. Given the physical device and window surfaces,
	 * the callback should return true to allow the device, or false to filter it out.
	 */
	public BoilerBuilder extraDeviceRequirements(ExtraDeviceRequirements requirements) {
		this.extraDeviceRequirements.add(requirements);
		return this;
	}

	/**
	 * When multiple physical devices satisfy all requirements, the physical device selector will choose 1 of them.
	 * The default selector prefers discrete GPUs over integrated GPUs over anything else. Chain this method to
	 * override this behavior.<br>
	 * You can either supply a callback, or an instance of SimpleDeviceSelector.
	 */
	public BoilerBuilder physicalDeviceSelector(PhysicalDeviceSelector selector) {
		this.deviceSelector = selector;
		return this;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// Device creation
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Enables the given device extensions, if and only if they are supported by the selected physical device. Any
	 * unsupported device extensions will be ignored.
	 */
	public BoilerBuilder desiredVkDeviceExtensions(Collection<String> deviceExtensions) {
		desiredVulkanDeviceExtensions.addAll(deviceExtensions);
		return this;
	}

	/**
	 * Adds a callback that can enable device features. Given a memory stack and the <i>supportedFeatures</i>,
	 * the callback can enable features in the <i>toEnable</i> features.
	 */
	public BoilerBuilder featurePicker10(FeaturePicker10 picker) {
		this.vkDeviceFeaturePicker10.add(picker);
		return this;
	}

	/**
	 * Adds a callback that can enable Vulkan 1.1 features. Given a memory stack and the <i>supportedFeatures</i>,
	 * the callback can enable features in the <i>toEnable</i> features.
	 */
	public BoilerBuilder featurePicker11(FeaturePicker11 picker) {
		checkApiVersion(VK_API_VERSION_1_1);
		this.vkDeviceFeaturePicker11.add(picker);
		return this;
	}

	/**
	 * Adds a callback that can enable Vulkan 1.2 features. Given a memory stack and the <i>supportedFeatures</i>,
	 * the callback can enable features in the <i>toEnable</i> features.
	 */
	public BoilerBuilder featurePicker12(FeaturePicker12 picker) {
		checkApiVersion(VK_API_VERSION_1_2);
		this.vkDeviceFeaturePicker12.add(picker);
		return this;
	}

	/**
	 * Adds a callback that can enable Vulkan 1.3 features. Given a memory stack and the <i>supportedFeatures</i>,
	 * the callback can enable features in the <i>toEnable</i> features.
	 */
	public BoilerBuilder featurePicker13(FeaturePicker13 picker) {
		checkApiVersion(VK_API_VERSION_1_3);
		this.vkDeviceFeaturePicker13.add(picker);
		return this;
	}

	/**
	 * Changes the <i>QueueFamilyMapper</i>: the function that determines which queue (families) are created, and which
	 * one will serve as the graphics queue, compute queue, transfer queue, etc...<br>
	 *
	 * The default implementation will try to use the same queue family for everything, and creates 1 queue per queue
	 * family. You can use this method to change this behavior.
	 */
	public BoilerBuilder queueFamilyMapper(QueueFamilyMapper mapper) {
		this.queueFamilyMapper = mapper;
		return this;
	}

	/**
	 * Adds a callback that will be called before the builder calls <i>vkCreateDevice</i>. Given the enabled instance
	 * extensions, the callback can choose to modify the <i>VkDeviceCreateInfo</i> before it's passed on to the device
	 * creator.
	 */
	public BoilerBuilder beforeDeviceCreation(PreVkDeviceCreator preCreator) {
		this.preDeviceCreators.add(preCreator);
		return this;
	}

	/**
	 * Changes the device creator. The default device creator will simply call `vkCreateDevice`, but you can do
	 * something else instead. Note that <i>beforeDeviceCreation</i> is preferred if you simply wish to alter the
	 * <i>VkDeviceCreateInfo</i>.
	 */
	public BoilerBuilder vkDeviceCreator(VkDeviceCreator creator) {
		if (this.vkDeviceCreator != DEFAULT_VK_DEVICE_CREATOR) {
			throw new IllegalStateException("Attempted to set multiple device creators");
		}
		this.vkDeviceCreator = creator;
		return this;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// Window functionality
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Ensures that a window will be created for the given <i>WindowBuilder</i> during the <i>build</i> method.
	 */
	public BoilerBuilder addWindow(WindowBuilder windowBuilder) {
		this.windows.add(windowBuilder);
		return this;
	}

	/**
	 * <p>
	 * Call this when you want the BoilerBuilder to create the window, but without initializing GLFW (e.g. when you
	 * want to initialize GLFW yourself).
	 * </p>
	 * <p>
	 * This method has no effect when the BoilerBuilder does <b>not</b> create a window (in that case, it won't
	 * initialize GLFW anyway).
	 * </p>
	 *
	 * @return this
	 */
	public BoilerBuilder dontInitGLFW() {
		this.initGLFW = false;
		return this;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// Miscellaneous
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Enables OpenXR (virtual/augmented reality) support
	 */
	public BoilerBuilder xr(BoilerXrBuilder xrBuilder) {
		this.xrBuilder = xrBuilder;
		return this;
	}

	/**
	 * Sets the default timeout (in nanoseconds) that the <i>BoilerInstance</i> and its children will use
	 * in e.g. <i>vkWaitForFences</i> and <i>vkAcquireNextImageKHR</i>. The default value is 1 second.
	 */
	public BoilerBuilder defaultTimeout(long defaultTimeout) {
		this.defaultTimeout = defaultTimeout;
		return this;
	}

	/**
	 * Filters out all physical devices that don't support dynamic rendering, and enables dynamic rendering on the
	 * <i>VkDevice</i> that will be created during the <i>build</i> method.
	 */
	public BoilerBuilder enableDynamicRendering() {
		this.dynamicRendering = true;
		return this;
	}

	private void checkApiVersion(int required) {
		if (VK_API_VERSION_MAJOR(apiVersion) < VK_API_VERSION_MAJOR(required)) {
			throw new UnsupportedOperationException("API major version is too low for this feature");
		}
		if (VK_API_VERSION_MAJOR(apiVersion) == VK_API_VERSION_MAJOR(required) &&
				VK_API_VERSION_MINOR(apiVersion) < VK_API_VERSION_MINOR(required)) {
			throw new UnsupportedOperationException("API minor version is too low for this feature");
		}
	}

	/**
	 * Builds the <i>BoilerInstance</i>. You should destroy it when you're finished using
	 * <i>boilerInstance.destroyInitialObjects()</i>.
	 * @return The <i>BoilerInstance</i>
	 * @throws GLFWFailureException If GLFW initialization failed, or GLFW-Vulkan interop is not available
	 * @throws VulkanFailureException If a Vulkan function called during this method doesn't return <i>VK_SUCCESS</i>
	 * @throws MissingVulkanLayerException If a <i>required</i> Vulkan (instance) layer is not supported
	 * @throws MissingVulkanExtensionException If a <i>required</i> Vulkan instance extension is not supported
	 * @throws NoVkPhysicalDeviceException If not a single <i>VkPhysicalDevice</i> satisfies all device requirements
	 * (or the target machine doesn't support any <i>VkPhysicalDevice</i> at all)
	 */
	public BoilerInstance build() throws GLFWFailureException, VulkanFailureException, MissingVulkanLayerException,
			MissingVulkanExtensionException, NoVkPhysicalDeviceException {
		if (didBuild) throw new IllegalStateException("This builder has been used already");
		didBuild = true;

		if (!windows.isEmpty() && windows.stream().allMatch(window -> window.glfwWindow == 0L) && initGLFW) {
			if (!glfwInit()) throw new GLFWFailureException("glfwInit() returned false");
		}

		for (var windowBuilder : windows) {
			if (windowBuilder.title == null) windowBuilder.title = applicationName;
			windowBuilder.createGlfwWindow();
		}

		boolean[] pHasSwapchainMaintenance = {false};

		if (!windows.isEmpty()) {
			if (!glfwVulkanSupported()) throw new GLFWFailureException("glfwVulkanSupported() returned false");
			var glfwExtensions = glfwGetRequiredInstanceExtensions();
			if (glfwExtensions == null)
				throw new GLFWFailureException("glfwGetRequiredInstanceExtensions() returned null");
			for (int extensionIndex = 0; extensionIndex < glfwExtensions.limit(); extensionIndex++) {
				this.requiredVulkanInstanceExtensions.add(memUTF8(glfwExtensions.get(extensionIndex)));
			}
			this.requiredVulkanDeviceExtensions.add(VK_KHR_SWAPCHAIN_EXTENSION_NAME);
			this.desiredVulkanDeviceExtensions.add(VK_EXT_SWAPCHAIN_MAINTENANCE_1_EXTENSION_NAME);
			this.desiredVulkanInstanceExtensions.add(VK_EXT_SURFACE_MAINTENANCE_1_EXTENSION_NAME);
			this.desiredVulkanInstanceExtensions.add(VK_KHR_GET_SURFACE_CAPABILITIES_2_EXTENSION_NAME);
			if (apiVersion == VK_API_VERSION_1_0) {
				this.desiredVulkanInstanceExtensions.add(VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME);
			}

			this.beforeDeviceCreation((ciDevice, instanceExtensions, physicalDevice, stack) -> {
				Set<String> deviceExtensions = decodeStringSet(ciDevice.ppEnabledExtensionNames());
				if (deviceExtensions.contains(VK_EXT_SWAPCHAIN_MAINTENANCE_1_EXTENSION_NAME)) {
					var swapchainFeatures = VkPhysicalDeviceSwapchainMaintenance1FeaturesEXT.calloc(stack);
					swapchainFeatures.sType$Default();

					var features = VkPhysicalDeviceFeatures2.calloc(stack);
					features.sType$Default();
					features.pNext(swapchainFeatures);

					if (apiVersion != VK_API_VERSION_1_0) {
						vkGetPhysicalDeviceFeatures2(physicalDevice, features);
					}
					if (instanceExtensions.contains(VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME)) {
						vkGetPhysicalDeviceFeatures2KHR(physicalDevice, features);
					}

					if (swapchainFeatures.swapchainMaintenance1()) {
						ciDevice.pNext(swapchainFeatures);
						pHasSwapchainMaintenance[0] = true;
					}
				}
			});
		}

		XrBoiler xr = null;

		if (xrBuilder != null) {
			xr = xrBuilder.build(
					this, validationFeatures != null, apiVersion,
					applicationName, applicationVersion, engineName, engineVersion
			);
		}

		// Nice for VMA
		if (apiVersion == VK_API_VERSION_1_0) {
			this.desiredVulkanInstanceExtensions.add(VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME);
			this.desiredVulkanDeviceExtensions.add(VK_KHR_DEDICATED_ALLOCATION_EXTENSION_NAME);
			this.desiredVulkanDeviceExtensions.add(VK_KHR_GET_MEMORY_REQUIREMENTS_2_EXTENSION_NAME);
			this.desiredVulkanDeviceExtensions.add(VK_KHR_BIND_MEMORY_2_EXTENSION_NAME);
			this.desiredVulkanDeviceExtensions.add(VK_EXT_MEMORY_BUDGET_EXTENSION_NAME);
		}

		if (validationFeatures != null) {
			this.requiredVulkanInstanceExtensions.add(VK_EXT_DEBUG_UTILS_EXTENSION_NAME);
			this.requiredVulkanInstanceExtensions.add(VK_EXT_VALIDATION_FEATURES_EXTENSION_NAME);
			this.requiredVulkanLayers.add("VK_LAYER_KHRONOS_validation");
		}

		if (dynamicRendering) {
			if (apiVersion < VK_API_VERSION_1_1) {
				this.requiredVulkanInstanceExtensions.add(VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME);
				this.requiredVulkanDeviceExtensions.add(VK_KHR_MULTIVIEW_EXTENSION_NAME);
				this.requiredVulkanDeviceExtensions.add(VK_KHR_MAINTENANCE_2_EXTENSION_NAME);
			}
			if (apiVersion < VK_API_VERSION_1_2) {
				this.requiredVulkanDeviceExtensions.add(VK_KHR_DEPTH_STENCIL_RESOLVE_EXTENSION_NAME);
				this.requiredVulkanDeviceExtensions.add(VK_KHR_CREATE_RENDERPASS_2_EXTENSION_NAME);
			}
			if (apiVersion < VK_API_VERSION_1_3) {
				this.requiredVulkanDeviceExtensions.add(VK_KHR_DYNAMIC_RENDERING_EXTENSION_NAME);
				this.extraDeviceRequirements.add((physicalDevice, x, stack) -> {
					var dynamicRendering = VkPhysicalDeviceDynamicRenderingFeaturesKHR.calloc(stack);
					dynamicRendering.sType$Default();

					var features2 = VkPhysicalDeviceFeatures2.calloc(stack);
					features2.sType$Default();
					features2.pNext(dynamicRendering);

					if (apiVersion >= VK_API_VERSION_1_1) vkGetPhysicalDeviceFeatures2(physicalDevice, features2);
					else vkGetPhysicalDeviceFeatures2KHR(physicalDevice, features2);

					return dynamicRendering.dynamicRendering();
				});
				this.preDeviceCreators.add((ciDevice, instanceExtensions, physicalDevice, stack) -> {
					var dynamicRendering = VkPhysicalDeviceDynamicRenderingFeatures.calloc(stack);
					dynamicRendering.sType$Default();
					dynamicRendering.dynamicRendering(true);

					ciDevice.pNext(dynamicRendering);
				});
			} else {
				this.vkRequiredFeatures13.add(VkPhysicalDeviceVulkan13Features::dynamicRendering);
				this.vkDeviceFeaturePicker13.add((stack, supported, toEnable) -> toEnable.dynamicRendering(true));
			}
		}

		var instanceResult = BoilerInstanceBuilder.createInstance(this);

		long validationErrorThrower = 0;
		if (forbidValidationErrors) {
			if (!instanceResult.enabledExtensions().contains(VK_EXT_DEBUG_UTILS_EXTENSION_NAME)) {
				throw new ValidationException("Debug utils extension is not enabled");
			}
			try (var stack = stackPush()) {
				var ciReporter = VkDebugUtilsMessengerCreateInfoEXT.calloc(stack);
				ciReporter.sType$Default();
				ciReporter.flags(0);
				ciReporter.messageSeverity(VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT);
				ciReporter.messageType(VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT);
				ciReporter.pfnUserCallback((severity, types, data, userData) -> {
					String message = VkDebugUtilsMessengerCallbackDataEXT.create(data).pMessageString();
					if ((severity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0) {
						throw new ValidationException(message);
					} else System.out.println(message);
					return VK_FALSE;
				});

				var pReporter = stack.callocLong(1);
				assertVkSuccess(vkCreateDebugUtilsMessengerEXT(
						instanceResult.vkInstance(), ciReporter, null, pReporter
				), "CreateDebugUtilsMessengerEXT", "Validation error thrower");
				validationErrorThrower = pReporter.get(0);
			}
		}

		var deviceResult = BoilerDeviceBuilder.createDevice(this, instanceResult);

		var windows = IntStream.range(0, this.windows.size()).mapToObj(windowIndex -> {
			var windowBuilder = this.windows.get(windowIndex);
			var vkSurface = deviceResult.windowSurfaces()[windowIndex];
			var presentFamily = deviceResult.presentFamilies()[windowIndex];
			return windowBuilder.build(deviceResult.vkPhysicalDevice(), vkSurface, pHasSwapchainMaintenance[0], presentFamily);
		}).collect(Collectors.toList());

		var instance = new BoilerInstance(
				xr, defaultTimeout, windows, pHasSwapchainMaintenance[0],
				apiVersion, instanceResult.vkInstance(), deviceResult.vkPhysicalDevice(), deviceResult.vkDevice(),
				instanceResult.enabledLayers(), instanceResult.enabledExtensions(), deviceResult.enabledExtensions(),
				deviceResult.queueFamilies(), deviceResult.vmaAllocator(), validationErrorThrower
		);
		if (xr != null) xr.boilerInstance = instance;

		for (int windowIndex = 0; windowIndex < windows.size(); windowIndex++) {
			this.windows.get(windowIndex).callback.accept(windows.get(windowIndex));
		}

		return instance;
	}
}
