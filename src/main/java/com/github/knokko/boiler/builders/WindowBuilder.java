package com.github.knokko.boiler.builders;

import com.github.knokko.boiler.builders.window.*;
import com.github.knokko.boiler.exceptions.GLFWFailureException;
import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.memory.callbacks.CallbackUserData;
import com.github.knokko.boiler.queues.VkbQueueFamily;
import com.github.knokko.boiler.window.VkbWindow;
import com.github.knokko.boiler.window.WindowProperties;
import org.lwjgl.vulkan.*;

import java.util.*;
import java.util.function.Consumer;

import static com.github.knokko.boiler.exceptions.SDLFailureException.assertSdlSuccess;
import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.glfwCreateWindowSurface;
import static org.lwjgl.sdl.SDLVideo.*;
import static org.lwjgl.sdl.SDLVulkan.SDL_Vulkan_CreateSurface;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memUTF8;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR;
import static org.lwjgl.vulkan.VK10.*;

public class WindowBuilder {

	final int width, height;
	final int swapchainImageUsage;
	long handle;
	String title;
	long sdlFlags = SDL_WINDOW_VULKAN | SDL_WINDOW_RESIZABLE;
	boolean hideUntilFirstFrame;
	SurfaceFormatPicker surfaceFormatPicker = new SimpleSurfaceFormatPicker(
			VK_FORMAT_R8G8B8A8_SRGB, VK_FORMAT_B8G8R8A8_SRGB
	);
	CompositeAlphaPicker compositeAlphaPicker = new SimpleCompositeAlphaPicker(
			VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR, VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR
	);
	Consumer<VkbWindow> callback = window -> {
	};
	Set<Integer> presentModes = new HashSet<>();

	/**
	 * @param width The initial width of the window, in pixels
	 * @param height The initial height of the window, in pixels
	 * @param swapchainImageUsage The image usage flags for the swapchain images to-be-created
	 */
	public WindowBuilder(int width, int height, int swapchainImageUsage) {
		this.width = width;
		this.height = height;
		this.swapchainImageUsage = swapchainImageUsage;
	}

	/**
	 * Sets the initial title of the window, by default the application name
	 */
	public WindowBuilder title(String title) {
		this.title = title;
		return this;
	}

	/**
	 * Specifies the <b>flags</b> parameter that will be passed to <i>SDL_CreateWindow</i>, if
	 * {@link BoilerBuilder#useSDL()} was chained. If you don't call this method, the flags
	 * will be {@code SDL_WINDOW_VULKAN | SDL_WINDOW_RESIZABLE}.
	 */
	public WindowBuilder sdlFlags(long flags) {
		this.sdlFlags = flags;
		return this;
	}

	/**
	 * Makes the window invisible until the first {@link org.lwjgl.vulkan.KHRSwapchain#vkQueuePresentKHR}
	 */
	public WindowBuilder hideUntilFirstFrame() {
		this.hideUntilFirstFrame = true;
		return this;
	}

	/**
	 * Changes the function that chooses the swapchain composite alpha. The default function will prefer opaque
	 * swapchains.
	 */
	public WindowBuilder compositeAlphaPicker(CompositeAlphaPicker compositeAlphaPicker) {
		this.compositeAlphaPicker = compositeAlphaPicker;
		return this;
	}

	/**
	 * Changes the function that chooses the surface format. The default function will prefer 8-bit SRGB formats.
	 */
	public WindowBuilder surfaceFormatPicker(SurfaceFormatPicker surfaceFormatPicker) {
		this.surfaceFormatPicker = surfaceFormatPicker;
		return this;
	}

	/**
	 * Adds present modes with which the swapchain manager should try to make its swapchains compatible.
	 * They will be ignored when the <i>VK_EXT_swapchain_maintenance1</i> extension is not enabled.
	 */
	public WindowBuilder presentModes(Integer... presentModes) {
		Collections.addAll(this.presentModes, presentModes);
		return this;
	}

	/**
	 * Lets this window builder use the existing glfwWindow or sdlWindow handle, instead of creating a new window.
	 * The title of this window builder will be ignored, as well as the initial width and height passed to the
	 * constructor.
	 */
	public WindowBuilder handle(long handle) {
		this.handle = handle;
		return this;
	}

	/**
	 * Adds a callback that will be called after the window and the <i>BoilerInstance</i> have been created. This
	 * is needed to obtain the <i>VkbWindow</i> instances when you add multiple windows.
	 */
	public WindowBuilder callback(Consumer<VkbWindow> callback) {
		this.callback = callback;
		return this;
	}

	VkbWindow build(
			VkPhysicalDevice vkPhysicalDevice, long vkSurface,
			boolean hasSwapchainMaintenance, VkbQueueFamily presentFamily
	) {
		try (var stack = stackPush()) {
			var capabilities = VkSurfaceCapabilitiesKHR.calloc(stack);
			assertVkSuccess(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(
					vkPhysicalDevice, vkSurface, capabilities
			), "GetPhysicalDeviceSurfaceCapabilitiesKHR", "BoilerSwapchainBuilder");

			var pNumFormats = stack.callocInt(1);
			assertVkSuccess(vkGetPhysicalDeviceSurfaceFormatsKHR(
					vkPhysicalDevice, vkSurface, pNumFormats, null
			), "GetPhysicalDeviceSurfaceFormatsKHR", "BoilerSwapchainBuilder-Count");
			int numFormats = pNumFormats.get(0);

			var pFormats = VkSurfaceFormatKHR.calloc(numFormats, stack);
			assertVkSuccess(vkGetPhysicalDeviceSurfaceFormatsKHR(
					vkPhysicalDevice, vkSurface, pNumFormats, pFormats
			), "GetPhysicalDeviceSurfaceFormatsKHR", "BoilerSwapchainBuilder-Elements");

			var formats = new HashSet<SurfaceFormat>(numFormats);
			for (int index = 0; index < numFormats; index++) {
				var format = pFormats.get(index);
				formats.add(new SurfaceFormat(format.format(), format.colorSpace()));
			}

			var pNumPresentModes = stack.callocInt(1);
			assertVkSuccess(vkGetPhysicalDeviceSurfacePresentModesKHR(
					vkPhysicalDevice, vkSurface, pNumPresentModes, null
			), "GetPhysicalDeviceSurfacePresentModesKHR", "BoilerSwapchainBuilder-Count");
			int numPresentModes = pNumPresentModes.get(0);

			var pPresentModes = stack.callocInt(numPresentModes);
			assertVkSuccess(vkGetPhysicalDeviceSurfacePresentModesKHR(
					vkPhysicalDevice, vkSurface, pNumPresentModes, pPresentModes
			), "GetPhysicalDeviceSurfacePresentModesKHR", "BoilerSwapchainBuilder-Count");

			var presentModes = new HashSet<Integer>(numPresentModes);
			for (int index = 0; index < numPresentModes; index++) {
				presentModes.add(pPresentModes.get(index));
			}

			var preparedPresentModes = new HashSet<>(this.presentModes);
			preparedPresentModes.removeIf(presentMode -> !presentModes.contains(presentMode));

			var surfaceFormat = surfaceFormatPicker.chooseSurfaceFormat(formats);
			var compositeAlpha = compositeAlphaPicker.chooseCompositeAlpha(capabilities.supportedCompositeAlpha());

			int numHiddenFrames = hideUntilFirstFrame ? 1 : 0; // TODO Allow more values
			int maxOldSwapchains = 0; // TODO Configure
			int maxFramesInFlight = 3; // TODO Configure
			var properties = new WindowProperties(
					handle, title, vkSurface, numHiddenFrames, surfaceFormat.format(), surfaceFormat.colorSpace(),
					swapchainImageUsage, compositeAlpha, hasSwapchainMaintenance, maxOldSwapchains, maxFramesInFlight
			);
			return new VkbWindow(properties, presentFamily, presentModes, preparedPresentModes);
		}
	}

	void createGlfwWindow() {
		if (handle != 0L) return;

		if (title == null) throw new IllegalStateException("Missing .title(...)");

		glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);

		// If I try this on Wayland, it will crash with:
		// xdg_wm_base@33: error 4: wl_surface@26 already has a buffer committed
		// hideUntilFirstFrame doesn't make much sense on Wayland anyway,
		// since Wayland always hides windows until the first frame is presented
		if (glfwGetPlatform() == GLFW_PLATFORM_WAYLAND) hideUntilFirstFrame = false;

		glfwWindowHint(GLFW_VISIBLE, hideUntilFirstFrame ? GLFW_FALSE : GLFW_TRUE);
		handle = glfwCreateWindow(width, height, title, 0L, 0L);
		if (handle == 0) {
			try (var stack = stackPush()) {
				var pError = stack.callocPointer(1);
				int errorCode = glfwGetError(pError);
				if (errorCode == GLFW_NO_ERROR) throw new GLFWFailureException("glfwCreateWindow() returned 0");
				else throw new GLFWFailureException(
						"glfwCreateWindow() returned 0 and glfwGetError() returned "
								+ errorCode + " because: " + memUTF8(pError.get())
				);
			}
		}
	}

	void createSdlWindow() {
		if (handle != 0L) return;
		if (title == null) throw new IllegalStateException("Missing .title(...)");

		// If I try this on Wayland, the window will never become visible, not even after SDL_ShowWindow
		// hideUntilFirstFrame doesn't make much sense on Wayland anyway,
		// since Wayland always hides windows until the first frame is presented
		if ("wayland".equals(SDL_GetCurrentVideoDriver())) hideUntilFirstFrame = false;

		if (hideUntilFirstFrame) sdlFlags |= SDL_WINDOW_HIDDEN;
		handle = SDL_CreateWindow(title, width, height, sdlFlags);
		assertSdlSuccess(handle != 0L, "CreateWindow");
	}

	/**
	 * Note: this method is for internal use only. Use `boilerInstance.addWindow` to add new windows.
	 */
	public VkbWindow buildLate(BoilerInstance instance) {
		long vkSurface;
		try (var stack = stackPush()) {
			var pSurface = stack.callocLong(1);
			if (instance.useSDL) {
				createSdlWindow();
				assertSdlSuccess(SDL_Vulkan_CreateSurface(
						handle, instance.vkInstance(),
						CallbackUserData.SURFACE.put(stack, instance), pSurface
				), "Vulkan_CreateSurface");
			} else {
				createGlfwWindow();
				assertVkSuccess(glfwCreateWindowSurface(
						instance.vkInstance(), handle, CallbackUserData.SURFACE.put(stack, instance), pSurface
				), "CreateWindowSurface", "WindowBuilder.buildLate");
			}

			vkSurface = pSurface.get(0);
		}

		var qf = instance.queueFamilies();
		List<VkbQueueFamily> candidateFamilies = new ArrayList<>();
		candidateFamilies.add(qf.graphics());
		if (!candidateFamilies.contains(qf.compute())) candidateFamilies.add(qf.compute());
		for (var family : qf.allEnabledFamilies()) {
			if (!candidateFamilies.contains(family)) candidateFamilies.add(family);
		}

		VkbQueueFamily presentFamily = null;
		try (var stack = stackPush()) {
			var pSupported = stack.callocInt(1);
			for (var family : candidateFamilies) {
				assertVkSuccess(vkGetPhysicalDeviceSurfaceSupportKHR(
						instance.vkPhysicalDevice(), family.index(), vkSurface, pSupported
				), "GetPhysicalDeviceSurfaceSupportKHR", "WindowBuilder.buildLate");
				if (pSupported.get(0) == VK_TRUE) {
					presentFamily = family;
					break;
				}
			}
		}

		if (presentFamily == null) {
			throw new UnsupportedOperationException("No enabled queue family supports this window surface");
		}

		var window = build(instance.vkPhysicalDevice(), vkSurface, instance.extra.swapchainMaintenance(), presentFamily);
		window.setInstance(instance);
		return window;
	}
}
