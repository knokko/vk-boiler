package com.github.knokko.boiler;

import com.github.knokko.boiler.builders.WindowBuilder;
import com.github.knokko.boiler.commands.BoilerCommands;
import com.github.knokko.boiler.debug.BoilerDebug;
import com.github.knokko.boiler.images.BoilerImages;
import com.github.knokko.boiler.memory.MemoryInfo;
import com.github.knokko.boiler.pipelines.BoilerPipelines;
import com.github.knokko.boiler.queues.QueueFamilies;
import com.github.knokko.boiler.synchronization.BoilerSync;
import com.github.knokko.boiler.window.VkbWindow;
import com.github.knokko.boiler.xr.XrBoiler;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import static org.lwjgl.util.vma.Vma.vmaDestroyAllocator;
import static org.lwjgl.vulkan.EXTDebugUtils.vkDestroyDebugUtilsMessengerEXT;
import static org.lwjgl.vulkan.VK10.*;

public class BoilerInstance {

	/**
	 * The default timeout for fence waits and swapchain waits, in nanoseconds
	 */
	public final long defaultTimeout;

	private final Collection<VkbWindow> windows;
	private final boolean hasSwapchainMaintenance;

	private final XrBoiler xr;

	/**
	 * The Vulkan API version that is being used
	 */
	public final int apiVersion;
	private final VkInstance vkInstance;
	private final VkPhysicalDevice vkPhysicalDevice;
	public final VkPhysicalDeviceProperties deviceProperties;
	private final VkDevice vkDevice;
	public final Set<String> explicitLayers, instanceExtensions, deviceExtensions;
	private final QueueFamilies queueFamilies;
	private final long vmaAllocator;
	private final long validationErrorThrower;

	public final MemoryInfo memoryInfo;
	public final BoilerImages images;
	public final BoilerPipelines pipelines;
	public final BoilerCommands commands;
	public final BoilerSync sync;
	public final BoilerDebug debug;

	private volatile boolean encounteredFatalValidationError;
	private volatile boolean destroyed = false;

	/**
	 * Note: using this constructor is allowed, but it is subject to change, even in non-major updates of
	 * vk-boiler. Therefor, using this constructor directly is not recommended. Use <i>BoilerBuilder</i> instead if
	 * you can.
	 */
	public BoilerInstance(
			XrBoiler xr, long defaultTimeout, Collection<VkbWindow> windows, boolean hasSwapchainMaintenance,
			int apiVersion, VkInstance vkInstance, VkPhysicalDevice vkPhysicalDevice, VkDevice vkDevice,
			Set<String> explicitLayers, Set<String> instanceExtensions, Set<String> deviceExtensions,
			QueueFamilies queueFamilies, long vmaAllocator, long validationErrorThrower
	) {
		this.windows = windows;
		this.hasSwapchainMaintenance = hasSwapchainMaintenance;
		this.xr = xr;
		this.defaultTimeout = defaultTimeout;
		this.apiVersion = apiVersion;
		this.vkInstance = vkInstance;
		this.vkPhysicalDevice = vkPhysicalDevice;
		this.vkDevice = vkDevice;
		this.explicitLayers = Collections.unmodifiableSet(explicitLayers);
		this.instanceExtensions = Collections.unmodifiableSet(instanceExtensions);
		this.deviceExtensions = Collections.unmodifiableSet(deviceExtensions);
		this.queueFamilies = queueFamilies;
		this.vmaAllocator = vmaAllocator;
		this.validationErrorThrower = validationErrorThrower;

		this.memoryInfo = new MemoryInfo(this);
		this.images = new BoilerImages(this);
		this.pipelines = new BoilerPipelines(this);
		this.commands = new BoilerCommands(this);
		this.sync = new BoilerSync(this);
		this.debug = new BoilerDebug(this);

		for (var window : windows) window.setInstance(this);
		this.deviceProperties = VkPhysicalDeviceProperties.calloc();
		vkGetPhysicalDeviceProperties(vkPhysicalDevice, deviceProperties);
	}

	private void checkDestroyed() {
		if (destroyed) throw new IllegalStateException("This instance has already been destroyed");
	}

	public void checkForFatalValidationErrors() {
		if (encounteredFatalValidationError) {
			throw new IllegalStateException("A fatal validation error has been encountered");
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
		checkDestroyed();
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
	 * @return True if and only if the swapchain maintenance extension can be used
	 */
	public boolean hasSwapchainMaintenance() {
		return hasSwapchainMaintenance;
	}

	/**
	 * Creates and returns an additional window. Note that it's better to add windows to the <i>BoilerBuilder</i>, but
	 * that only works if you know upfront how many windows you need.
	 * @param builder The <i>WindowBuilder</i> containing all window creation parameters
	 * @return The created window
	 */
	public VkbWindow addWindow(WindowBuilder builder) {
		checkDestroyed();
		return builder.buildLate(this);
	}

	public XrBoiler xr() {
		checkDestroyed();
		if (xr == null) throw new UnsupportedOperationException("This BoilerInstance doesn't have OpenXR support");
		return xr;
	}

	public VkInstance vkInstance() {
		checkDestroyed();
		return vkInstance;
	}

	public VkPhysicalDevice vkPhysicalDevice() {
		checkDestroyed();
		return vkPhysicalDevice;
	}

	public VkDevice vkDevice() {
		checkDestroyed();
		return vkDevice;
	}

	public QueueFamilies queueFamilies() {
		checkDestroyed();
		return queueFamilies;
	}

	public long vmaAllocator() {
		checkDestroyed();
		return vmaAllocator;
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
		checkDestroyed();

		for (var window : windows) window.destroy();
		sync.fenceBank.destroy();
		sync.semaphoreBank.destroy();
		vmaDestroyAllocator(vmaAllocator);
		vkDestroyDevice(vkDevice, null);
		if (validationErrorThrower != VK_NULL_HANDLE) {
			vkDestroyDebugUtilsMessengerEXT(vkInstance, validationErrorThrower, null);
		}
		vkDestroyInstance(vkInstance, null);
		if (xr != null) xr.destroyInitialObjects();

		deviceProperties.free();
		destroyed = true;
	}
}
