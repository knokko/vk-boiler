package com.github.knokko.boiler.samples;

import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.builders.WindowBuilder;
import com.github.knokko.boiler.builders.device.SimpleDeviceSelector;
import com.github.knokko.boiler.builders.instance.ValidationFeatures;
import com.github.knokko.boiler.builders.window.SimpleCompositeAlphaPicker;
import com.github.knokko.boiler.commands.CommandRecorder;
import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.synchronization.ResourceUsage;
import com.github.knokko.boiler.window.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSurface;

import static com.github.knokko.boiler.utilities.ReflectionHelper.getIntConstantName;
import static java.lang.Math.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.memUTF8;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.VK10.*;

public class TranslucentWindowPlayground extends SimpleWindowRenderLoop {

	public static void main(String[] args) throws InterruptedException {
		//noinspection resource
		glfwSetErrorCallback(
				(error, description) -> System.out.println("[GLFW]: " + memUTF8(description) + " (" + error + ")")
		);

		// Wayland's windows have cool transparency support
		if (glfwPlatformSupported(GLFW_PLATFORM_WAYLAND)) glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_WAYLAND);
		if (!glfwInit()) {
			// The fact that glfwPlatFormSupported(GLFW_PLATFORM_WAYLAND) returned `true` does not necessarily mean
			// that this machine actually supports Wayland. If Wayland is not supported by this machine, glfwInit()
			// will fail (return false), and we retry with X11.
			if (glfwPlatformSupported(GLFW_PLATFORM_WAYLAND) && glfwPlatformSupported(GLFW_PLATFORM_X11)) {
				glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_X11);
				System.out.println("This machine doesn't seem to support Wayland; falling back to X11...");
			}
			if (!glfwInit()) throw new RuntimeException("Failed to init GLFW");
		}
		glfwWindowHint(GLFW_TRANSPARENT_FRAMEBUFFER, GLFW_TRUE);

		var boiler = new BoilerBuilder(
				VK_API_VERSION_1_0, "TranslucentWindowPlayground", VK_MAKE_VERSION(1, 0, 0)
		)
				.validation(new ValidationFeatures(
						false, false, false, true, true
				))
				.dontInitGLFW()
				.addWindow(new WindowBuilder(
						800, 600, VK_IMAGE_USAGE_TRANSFER_DST_BIT
				).compositeAlphaPicker(new SimpleCompositeAlphaPicker(
						VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR,
						VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR,
						VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR,
						VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR
				)))
				// Avoid annoying crashes on laptops with multiple GPUs by preferring the integrated GPU
				.physicalDeviceSelector(new SimpleDeviceSelector(VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU))
				.build();

		System.out.printf(
				"GLFW platform is %s and GLFW transparent framebuffer is %b and composite alpha mode is %s\n",
				getIntConstantName(GLFW.class, glfwGetPlatform(), "GLFW_PLATFORM", "", "unknown"),
				glfwGetWindowAttrib(boiler.window().glfwWindow, GLFW_TRANSPARENT_FRAMEBUFFER),
				getIntConstantName(KHRSurface.class, boiler.window().swapchainCompositeAlpha,
						"VK_COMPOSITE_ALPHA", "BIT_KHR", "unknown")
		);

		var eventLoop = new WindowEventLoop();
		eventLoop.addWindow(new TranslucentWindowPlayground(boiler.window()));
		eventLoop.runMain();

		boiler.destroyInitialObjects();
	}

	public TranslucentWindowPlayground(VkbWindow window) {
		super(
				window, 5, false, VK_PRESENT_MODE_MAILBOX_KHR,
				ResourceUsage.TRANSFER_DEST, ResourceUsage.TRANSFER_DEST
		);
	}

	@Override
	protected void recordFrame(
			MemoryStack stack, CommandRecorder recorder, AcquiredImage acquired, BoilerInstance boiler
	) {
		float alpha = 0.1f + 0.9f * (float) (abs(sin(System.currentTimeMillis() / 250.0)));
		float colorScale = boiler.window().swapchainCompositeAlpha == VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR ? 1f : alpha;
		recorder.clearColorImage(acquired.image().vkImage(), 0f, 0.6f * colorScale, colorScale, alpha);
	}
}
