package com.github.knokko.boiler.builder;

import com.github.knokko.boiler.builder.window.*;
import com.github.knokko.boiler.exceptions.GLFWFailureException;
import com.github.knokko.boiler.instance.BoilerInstance;
import com.github.knokko.boiler.queue.QueueFamily;
import com.github.knokko.boiler.window.VkbWindow;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;

import java.util.*;
import java.util.function.Consumer;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.glfwCreateWindowSurface;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memUTF8;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR;
import static org.lwjgl.vulkan.VK10.*;

public class WindowBuilder {

	final int width, height;
	final int swapchainImageUsage;
	long glfwWindow;
	String title;
	SurfaceFormatPicker surfaceFormatPicker = new SimpleSurfaceFormatPicker(
			VK_FORMAT_R8G8B8A8_SRGB, VK_FORMAT_B8G8R8A8_SRGB
	);
	CompositeAlphaPicker compositeAlphaPicker = new SimpleCompositeAlphaPicker(
			VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR, VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR
	);
	Consumer<VkbWindow> callback = window -> {
	};
	Set<Integer> presentModes = new HashSet<>();

	public WindowBuilder(int width, int height, int swapchainImageUsage) {
		this.width = width;
		this.height = height;
		this.swapchainImageUsage = swapchainImageUsage;
	}

	public WindowBuilder glfwWindow(long glfwWindow) {
		this.glfwWindow = glfwWindow;
		return this;
	}

	public WindowBuilder title(String title) {
		this.title = title;
		return this;
	}

	public WindowBuilder surfaceFormatPicker(SurfaceFormatPicker surfaceFormatPicker) {
		this.surfaceFormatPicker = surfaceFormatPicker;
		return this;
	}

	public WindowBuilder compositeAlphaPicker(CompositeAlphaPicker compositeAlphaPicker) {
		this.compositeAlphaPicker = compositeAlphaPicker;
		return this;
	}

	public WindowBuilder callback(Consumer<VkbWindow> callback) {
		this.callback = callback;
		return this;
	}

	public WindowBuilder presentModes(Integer... presentModes) {
		Collections.addAll(this.presentModes, presentModes);
		return this;
	}

	VkbWindow build(
			VkPhysicalDevice vkPhysicalDevice, long vkSurface,
			boolean hasSwapchainMaintenance, QueueFamily presentFamily
	) {
		// Note: do NOT allocate the capabilities on the stack because it needs to be read later!
		var capabilities = VkSurfaceCapabilitiesKHR.calloc();

		try (var stack = stackPush()) {
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

			return new VkbWindow(
					hasSwapchainMaintenance, glfwWindow, vkSurface, presentModes, preparedPresentModes, title,
					surfaceFormat.format(), surfaceFormat.colorSpace(), capabilities,
					swapchainImageUsage, compositeAlpha, presentFamily
			);
		}
	}

	void createGlfwWindow() {
		if (glfwWindow != 0L) return;

		if (title == null) throw new IllegalStateException("Missing .title(...)");

		glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
		glfwWindow = glfwCreateWindow(width, height, title, 0L, 0L);
		if (glfwWindow == 0) {
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

	public VkbWindow buildLate(BoilerInstance instance) {
		createGlfwWindow();

		long vkSurface;
		try (var stack = stackPush()) {
			var pSurface = stack.callocLong(1);
			assertVkSuccess(glfwCreateWindowSurface(
					instance.vkInstance(), glfwWindow, null, pSurface
			), "CreateWindowSurface", "WindowBuilder.buildLate");
			vkSurface = pSurface.get(0);
		}

		var qf = instance.queueFamilies();
		List<QueueFamily> candidateFamilies = new ArrayList<>();
		candidateFamilies.add(qf.graphics());
		if (!candidateFamilies.contains(qf.compute())) candidateFamilies.add(qf.compute());
		for (var family : qf.allEnabledFamilies()) {
			if (!candidateFamilies.contains(family)) candidateFamilies.add(family);
		}

		QueueFamily presentFamily = null;
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

		var window = build(instance.vkPhysicalDevice(), vkSurface, instance.hasSwapchainMaintenance(), presentFamily);
		window.setInstance(instance);
		return window;
	}
}
