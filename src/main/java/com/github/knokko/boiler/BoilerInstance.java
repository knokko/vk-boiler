package com.github.knokko.boiler;

import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.builders.WindowBuilder;
import com.github.knokko.boiler.commands.BoilerCommands;
import com.github.knokko.boiler.debug.BoilerDebug;
import com.github.knokko.boiler.debug.ValidationException;
import com.github.knokko.boiler.images.BoilerImages;
import com.github.knokko.boiler.memory.MemoryInfo;
import com.github.knokko.boiler.memory.callbacks.CallbackUserData;
import com.github.knokko.boiler.memory.callbacks.VkbAllocationCallbacks;
import com.github.knokko.boiler.pipelines.BoilerPipelines;
import com.github.knokko.boiler.queues.QueueFamilies;
import com.github.knokko.boiler.synchronization.BoilerSync;
import com.github.knokko.boiler.window.VkbWindow;
import com.github.knokko.boiler.xr.XrBoiler;
import org.lwjgl.vulkan.*;

import java.util.Collection;
import java.util.concurrent.locks.ReadWriteLock;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.vma.Vma.vmaDestroyAllocator;
import static org.lwjgl.vulkan.EXTDebugUtils.vkDestroyDebugUtilsMessengerEXT;
import static org.lwjgl.vulkan.KHRGetPhysicalDeviceProperties2.VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRGetPhysicalDeviceProperties2.vkGetPhysicalDeviceProperties2KHR;
import static org.lwjgl.vulkan.KHRMaintenance3.VK_KHR_MAINTENANCE3_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.vkGetPhysicalDeviceProperties2;

public class BoilerInstance {

	/**
	 * The default timeout for fence waits and swapchain waits, in nanoseconds
	 */
	public final long defaultTimeout;

	/**
	 * <ul>
	 *     <li>When <b>true</b>, this instance will use SDL to create windows</li>
	 *     <li>When <b>false</b>, this instance will use GLFW to create windows</li>
	 * </ul>
	 * This is <b>false</b> by default, but can be changed using {@link BoilerBuilder#useSDL()}
	 */
	public final boolean useSDL;

	private final Collection<VkbWindow> windows;

	private final XrBoiler xr;

	/**
	 * The Vulkan API version that is being used
	 */
	public final int apiVersion;
	private final VkInstance vkInstance;
	private final VkPhysicalDevice vkPhysicalDevice;

	private final VkPhysicalDeviceProperties2 deviceProperties2;

	/**
	 * An instance of {@link VkPhysicalDeviceProperties} that is allocated and queried when this boiler instance is
	 * created, and freed when this boiler instance is destroyed. This field can be used to conveniently read the
	 * device properties throughout the lifetime of this boiler instance.
	 */
	public final VkPhysicalDeviceProperties deviceProperties;

	/**
	 * <p>
	 *     When this is non-null, it is an instance of {@link VkPhysicalDeviceMaintenance3Properties} that is
	 *     allocated and queried when this boiler instance is created, and freed when this boiler instance is destroyed.
	 *     This field is used by the {@link com.github.knokko.boiler.memory.MemoryCombiner} to check the
	 *     maximum allocation size.
	 * </p>
	 *
	 * <p>
	 *     This field will be <b>null</b> when Vulkan 1.0 is used <i>and</i> the driver does <i>not</i> support
	 *     <i>VK_KHR_MAINTENANCE3</i>. Since almost all drivers support this extension, this field should rarely be
	 *     <b>null</b>.
	 * </p>
	 */
	public final VkPhysicalDeviceMaintenance3Properties maintenance3Properties;

	private final VkDevice vkDevice;

	/**
	 * Captures the layers, instance extensions, and device extensions that were enabled, as well as some features that
	 * are important for vk-boiler internally.
	 */
	public final BoilerExtra extra;
	private final QueueFamilies queueFamilies;
	private final long vmaAllocator;
	private final long validationErrorThrower;

	/**
	 * The allocation callbacks that were supplied to
	 * {@link com.github.knokko.boiler.builders.BoilerBuilder#allocationCallbacks(VkbAllocationCallbacks)}, or
	 * <b>null</b> when no allocation callbacks were supplied.
	 * It will be passed as <i>pAllocator</i> to every Vulkan creation function and destruction function that this
	 * {@link BoilerInstance} or its children call 'behind the scenes'.
	 */
	public final VkbAllocationCallbacks allocationCallbacks;

	public final MemoryInfo memoryInfo;
	public final BoilerImages images;
	public final BoilerPipelines pipelines;
	public final BoilerCommands commands;
	public final BoilerSync sync;
	public final BoilerDebug debug;

	/**
	 * This lock is used to ensure that no queue submissions can happen at the same time as a {@code vkDeviceWaitIdle()}
	 * (which is forbidden by the specification).
	 * <ul>
	 *     <li>
	 *         Any {@link com.github.knokko.boiler.queues.VkbQueue} will hold the read lock while they are calling
	 *         {@code vkQueueSubmit} or {@code vkQueuePresentKHR}
	 *     </li>
	 *     <li>
	 *         The write lock will be held during {@link #deviceWaitIdle(String)}
	 *     </li>
	 * </ul>
	 */
	public final ReadWriteLock waitIdleLock;

	private volatile boolean encounteredFatalValidationError;
	private volatile boolean destroyed = false;

	/**
	 * Note: using this constructor is allowed, but it is subject to change, even in non-major updates of
	 * vk-boiler. Therefor, using this constructor directly is not recommended. Use <i>BoilerBuilder</i> instead if
	 * you can.
	 */
	public BoilerInstance(
			XrBoiler xr, long defaultTimeout, boolean useSDL, Collection<VkbWindow> windows,
			int apiVersion, VkInstance vkInstance, VkPhysicalDevice vkPhysicalDevice, VkDevice vkDevice,
			BoilerExtra extra, QueueFamilies queueFamilies, ReadWriteLock waitIdleLock,
			long vmaAllocator, long validationErrorThrower, VkbAllocationCallbacks allocationCallbacks
	) {
		this.allocationCallbacks = allocationCallbacks;
		this.useSDL = useSDL;
		this.windows = windows;
		this.xr = xr;
		this.apiVersion = apiVersion;
		this.vkInstance = vkInstance;
		this.vkPhysicalDevice = vkPhysicalDevice;
		this.vkDevice = vkDevice;
		this.extra = extra;
		this.queueFamilies = queueFamilies;
		this.waitIdleLock = waitIdleLock;
		this.vmaAllocator = vmaAllocator;
		this.validationErrorThrower = validationErrorThrower;

		this.debug = new BoilerDebug(this);
		this.memoryInfo = new MemoryInfo(this);
		this.images = new BoilerImages(this);
		this.pipelines = new BoilerPipelines(this);
		this.commands = new BoilerCommands(this);
		this.sync = new BoilerSync(this);

		for (var window : windows) window.setInstance(this);
		if (VK_API_VERSION_MAJOR(apiVersion) == 1 && VK_API_VERSION_MINOR(apiVersion) == 0) {
			boolean hasMaintenance3 = extra.deviceExtensions().contains(VK_KHR_MAINTENANCE3_EXTENSION_NAME);
			boolean hasProperties2 = extra.instanceExtensions().contains(
					VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME
			);
			if (hasMaintenance3 && hasProperties2) {
				this.deviceProperties2 = VkPhysicalDeviceProperties2KHR.calloc();
				this.deviceProperties2.sType$Default();
				this.deviceProperties = deviceProperties2.properties();
				this.maintenance3Properties = VkPhysicalDeviceMaintenance3PropertiesKHR.calloc();
				this.maintenance3Properties.sType$Default();
				deviceProperties2.pNext(maintenance3Properties);
				vkGetPhysicalDeviceProperties2KHR(vkPhysicalDevice, deviceProperties2);
			} else {
				this.deviceProperties2 = null;
				this.deviceProperties = VkPhysicalDeviceProperties.calloc();
				vkGetPhysicalDeviceProperties(vkPhysicalDevice, deviceProperties);
				this.maintenance3Properties = null;
			}
		} else {
			this.deviceProperties2 = VkPhysicalDeviceProperties2.calloc();
			this.deviceProperties2.sType$Default();
			this.deviceProperties = deviceProperties2.properties();
			this.maintenance3Properties = VkPhysicalDeviceMaintenance3Properties.calloc();
			this.maintenance3Properties.sType$Default();
			deviceProperties2.pNext(maintenance3Properties);
			vkGetPhysicalDeviceProperties2(vkPhysicalDevice, deviceProperties2);
		}
		if (defaultTimeout == 0L) {
			if (deviceProperties.deviceType() == VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU ||
					deviceProperties.deviceType() == VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU) {
				defaultTimeout = 1_000_000_000L;
			} else {
				defaultTimeout = 60_000_000_000L;
			}
		}

		this.defaultTimeout = defaultTimeout;
	}

	public void checkForFatalValidationErrors() {
		if (destroyed) throw new IllegalStateException("This instance has already been destroyed");
		if (debug.hasDebug && encounteredFatalValidationError) {
			throw new ValidationException("A fatal validation error has been encountered earlier");
		}
	}

	public void reportFatalValidationError() {
		encounteredFatalValidationError = true;
	}

	/**
	 * Gets the window that was created by the BoilerBuilder. This method only works if exactly 1 window was created.
	 * If you create multiple windows, you need to chain <i>.callback(...)</i> to the <i>WindowBuilder</i>
	 */
	public VkbWindow window() {
		checkForFatalValidationErrors();
		if (windows.isEmpty()) throw new UnsupportedOperationException("This boiler doesn't have a window");
		if (windows.size() > 1) {
			throw new UnsupportedOperationException(
					"This method can't be used when there are multiple windows. " +
							"You should chain a .callback(...) to the corresponding WindowBuilder to get its instance"
			);
		}
		return windows.iterator().next();
	}

	/**
	 * Creates and returns an additional window. Note that it's better to add windows to the <i>BoilerBuilder</i>, but
	 * that only works if you know upfront how many windows you need.
	 * @param builder The <i>WindowBuilder</i> containing all window creation parameters
	 * @return The created window
	 */
	public VkbWindow addWindow(WindowBuilder builder) {
		checkForFatalValidationErrors();
		return builder.buildLate(this);
	}

	public XrBoiler xr() {
		checkForFatalValidationErrors();
		if (xr == null) throw new UnsupportedOperationException("This BoilerInstance doesn't have OpenXR support");
		return xr;
	}

	public VkInstance vkInstance() {
		checkForFatalValidationErrors();
		return vkInstance;
	}

	public VkPhysicalDevice vkPhysicalDevice() {
		checkForFatalValidationErrors();
		return vkPhysicalDevice;
	}

	public VkDevice vkDevice() {
		checkForFatalValidationErrors();
		return vkDevice;
	}

	public QueueFamilies queueFamilies() {
		checkForFatalValidationErrors();
		return queueFamilies;
	}

	public long vmaAllocator() {
		checkForFatalValidationErrors();
		return vmaAllocator;
	}

	/**
	 * Calls {@code vkDeviceWaitIdle} while holding the write-lock of {@link #waitIdleLock}
	 * @param context When {@code vkDeviceWaitIdle} fails (doesn't return {@code VK_SUCCESS}, this context will be
	 *                included in the exception message.
	 */
	public void deviceWaitIdle(String context) {
		waitIdleLock.writeLock().lock();
		try {
			assertVkSuccess(vkDeviceWaitIdle(vkDevice), "DeviceWaitIdle", context);
		} finally {
			waitIdleLock.writeLock().unlock();
		}
	}

	/**
	 * Destroys the GLFW objects and the Vulkan objects that were created during <i>BoilerBuilder.build()</i> (or were
	 * passed to the constructor of this class if <i>BoilerBuilder</i> wasn't used). A list of objects that will be
	 * destroyed:
	 * <ul>
	 *     <li>All windows (if any), alongside their swapchains and surfaces</li>
	 *     <li>The returned fences in the fence bank</li>
	 *     <li>The unused semaphores in the semaphore bank</li>
	 *     <li>The VMA allocator</li>
	 *     <li>The VkDevice</li>
	 *     <li>The validation error thrower (if applicable)</li>
	 *     <li>The VkInstance</li>
	 *     <li>The OpenXR instance (if applicable)</li>
	 * </ul>
	 */
	public void destroyInitialObjects() {
		checkForFatalValidationErrors();

		try (var stack = stackPush()) {
			for (var window : windows) window.destroy();
			sync.fenceBank.destroy();
			sync.semaphoreBank.destroy();
			if (vmaAllocator != VK_NULL_HANDLE) vmaDestroyAllocator(vmaAllocator);
			vkDestroyDevice(vkDevice, CallbackUserData.DEVICE.put(stack, allocationCallbacks));
			if (validationErrorThrower != VK_NULL_HANDLE) {
				vkDestroyDebugUtilsMessengerEXT(
						vkInstance, validationErrorThrower,
						CallbackUserData.DEBUG_MESSENGER.put(stack, allocationCallbacks)
				);
			}
			vkDestroyInstance(vkInstance, CallbackUserData.INSTANCE.put(stack, allocationCallbacks));
			if (xr != null) xr.destroyInitialObjects();
		}

		if (deviceProperties2 != null) deviceProperties2.free();
		else deviceProperties.free();
		destroyed = true;

		// This check is nice for unit tests: it gives the validation layer 100 ms to report async validation errors,
		// causing the unit test to fail if that happens.
		if (debug.hasDebug) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException skipSleeping) {
				// Ok, let's move on
			}
			if (encounteredFatalValidationError) {
				throw new ValidationException("A fatal validation error has been encountered earlier");
			}
		}
	}
}
