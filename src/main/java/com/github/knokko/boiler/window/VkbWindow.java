package com.github.knokko.boiler.window;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.queues.VkbQueueFamily;
import com.github.knokko.boiler.synchronization.AwaitableSubmission;
import org.lwjgl.vulkan.*;

import java.util.*;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static java.lang.Math.max;
import static org.lwjgl.glfw.GLFW.glfwDestroyWindow;
import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRGetSurfaceCapabilities2.vkGetPhysicalDeviceSurfaceCapabilities2KHR;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.VK_OBJECT_TYPE_SWAPCHAIN_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkCreateSwapchainKHR;
import static org.lwjgl.vulkan.VK10.*;

/**
 * A thick wrapper around a <i>GlfwWindow</i>, and a potential manager for the swapchains of the window. See
 * docs/swapchain.md for instructions on how to use this.
 */
public class VkbWindow {

	BoilerInstance instance;
	private final SwapchainCleaner cleaner;
	public final long glfwWindow;
	public final long vkSurface;
	/**
	 * An immutable set of all supported <i>VkPresentModeKHR</i>s of the <i>VkSurfaceKHR</i> of this window.
	 */
	public final Set<Integer> supportedPresentModes;
	private final Set<Integer> usedPresentModes;
	/**
	 * The <i>VkFormat</i> of the swapchain images of the <i>VkSurfaceKHR</i> of this window
	 */
	public final int surfaceFormat;
	/**
	 * The <i>VkColorSpaceKHR</i> of the swapchain images of the <i>VkSurfaceKHR</i> of this window
	 */
	public final int surfaceColorSpace;
	private final VkSurfaceCapabilitiesKHR surfaceCapabilities;
	/**
	 * The image usage flags for the swapchain images that will be created for this window
	 */
	public final int swapchainImageUsage;
	/**
	 * The composite alpha for the swapchain images that will be created for this window
	 */
	public final int swapchainCompositeAlpha;
	/**
	 * The queue family that will be used to present images of the swapchains for this window, typically the
	 * 'main' graphics queue family
	 */
	public final VkbQueueFamily presentFamily;

	private int width, height;
	private VkbSwapchain currentSwapchain;
	private long currentSwapchainID;
	private final String title;

	private boolean hasBeenDestroyed;

	WindowEventLoop windowLoop;

	/**
	 * This constructor is meant for internal use only. You should use <i>BoilerBuilder.addWindow</i> or
	 * <i>BoilerInstance.addWindow</i> instead
	 */
	public VkbWindow(
			boolean hasSwapchainMaintenance, long glfwWindow, long vkSurface,
			Collection<Integer> supportedPresentModes, Set<Integer> preparedPresentModes,
			String title, int surfaceFormat, int surfaceColorSpace, VkSurfaceCapabilitiesKHR surfaceCapabilities,
			int swapchainImageUsage, int swapchainCompositeAlpha, VkbQueueFamily presentFamily
	) {
		if (hasSwapchainMaintenance) cleaner = new SwapchainMaintenanceCleaner();
		else cleaner = new LegacySwapchainCleaner();
		this.glfwWindow = glfwWindow;
		this.vkSurface = vkSurface;
		this.supportedPresentModes = Set.copyOf(supportedPresentModes);
		this.usedPresentModes = new HashSet<>(preparedPresentModes);
		this.surfaceFormat = surfaceFormat;
		this.surfaceColorSpace = surfaceColorSpace;
		this.surfaceCapabilities = Objects.requireNonNull(surfaceCapabilities);
		this.swapchainImageUsage = swapchainImageUsage;
		this.swapchainCompositeAlpha = swapchainCompositeAlpha;
		this.presentFamily = presentFamily;
		this.title = title;
	}

	public void setInstance(BoilerInstance instance) {
		if (this.instance != null) throw new IllegalStateException();
		this.instance = instance;
		this.cleaner.instance = instance;
		this.updateSize();
	}

	/**
	 * Acquires a swapchain image that will be available after waiting on its <i>acquireFence</i>
	 * @param presentMode The present mode that will be used to present the swapchain image
	 * @return The acquired swapchain image, or null if no image can be acquired now
	 * (e.g. because the window is minimized)
	 */
	public AcquiredImage acquireSwapchainImageWithFence(int presentMode) {
		return acquireSwapchainImage(presentMode, true);
	}

	/**
	 * Acquires a swapchain image that will be available after its <i>acquireSemaphore</i> has
	 * been signaled.
	 * @param presentMode The present mode that will be used to present the swapchain image
	 * @return The acquired swapchain image, or null if no image can be acquired now
	 * (e.g. because the window is minimized)
	 */
	public AcquiredImage acquireSwapchainImageWithSemaphore(int presentMode) {
		return acquireSwapchainImage(presentMode, false);
	}

	private void assertMainThread() {
		if (!Thread.currentThread().getName().equals("main")) throw new Error("updateSize must happen on main thread");
	}

	private void updateSize() {
		assertMainThread();
		try (var stack = stackPush()) {
			assertVkSuccess(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(
					instance.vkPhysicalDevice(), vkSurface, surfaceCapabilities
			), "GetPhysicalDeviceSurfaceCapabilitiesKHR", "Update window size");
			int width = surfaceCapabilities.currentExtent().width();
			int height = surfaceCapabilities.currentExtent().height();

			if (width == -1 || height == -1) {
				var pWidth = stack.callocInt(1);
				var pHeight = stack.callocInt(1);
				glfwGetFramebufferSize(glfwWindow, pWidth, pHeight);
				width = pWidth.get(0);
				height = pHeight.get(0);
			}

			this.width = width;
			this.height = height;
		}
	}

	/**
	 * @return The current (or very recent) width of the window, in pixels
	 */
	public int getWidth() {
		return width;
	}

	/**
	 * @return The current (or very recent) height of the window, in pixels
	 */
	public int getHeight() {
		return height;
	}

	private void createSwapchain(int presentMode, boolean updateSize) {
		assertMainThread();
		try (var stack = stackPush()) {
			if (updateSize) updateSize();
			if (width == 0 || height == 0) {
				currentSwapchain = null;
				return;
			}

			int desiredImageCount = presentMode == VK_PRESENT_MODE_MAILBOX_KHR ? 3 : 2;
			var ciSwapchain = VkSwapchainCreateInfoKHR.calloc(stack);
			ciSwapchain.sType$Default();
			ciSwapchain.flags(0);
			ciSwapchain.surface(vkSurface);
			ciSwapchain.minImageCount(max(desiredImageCount, surfaceCapabilities.minImageCount()));
			ciSwapchain.imageFormat(surfaceFormat);
			ciSwapchain.imageColorSpace(surfaceColorSpace);
			ciSwapchain.imageExtent().set(width, height);
			ciSwapchain.imageArrayLayers(1);
			ciSwapchain.imageUsage(swapchainImageUsage);

			if (instance.queueFamilies().graphics() == presentFamily) {
				ciSwapchain.imageSharingMode(VK_SHARING_MODE_EXCLUSIVE);
			} else {
				ciSwapchain.imageSharingMode(VK_SHARING_MODE_CONCURRENT);
				ciSwapchain.queueFamilyIndexCount(2);
				ciSwapchain.pQueueFamilyIndices(stack.ints(
						instance.queueFamilies().graphics().index(), presentFamily.index()
				));
			}

			ciSwapchain.preTransform(surfaceCapabilities.currentTransform());
			ciSwapchain.compositeAlpha(swapchainCompositeAlpha);
			ciSwapchain.presentMode(presentMode);
			ciSwapchain.clipped(true);
			ciSwapchain.oldSwapchain(cleaner.getLatestSwapchainHandle());

			Set<Integer> compatibleUsedPresentModes = new HashSet<>();
			compatibleUsedPresentModes.add(presentMode);

			if (instance.hasSwapchainMaintenance() && usedPresentModes.size() > 1) {
				var presentModeCompatibility = VkSurfacePresentModeCompatibilityEXT.calloc(stack);
				presentModeCompatibility.sType$Default();

				var queriedPresentMode = VkSurfacePresentModeEXT.calloc(stack);
				queriedPresentMode.sType$Default();
				queriedPresentMode.presentMode(presentMode);

				var surfaceInfo = VkPhysicalDeviceSurfaceInfo2KHR.calloc(stack);
				surfaceInfo.sType$Default();
				surfaceInfo.pNext(queriedPresentMode);
				surfaceInfo.surface(vkSurface);

				var surfaceCapabilities2 = VkSurfaceCapabilities2KHR.calloc(stack);
				surfaceCapabilities2.sType$Default();
				surfaceCapabilities2.pNext(presentModeCompatibility);

				assertVkSuccess(vkGetPhysicalDeviceSurfaceCapabilities2KHR(
						instance.vkPhysicalDevice(), surfaceInfo, surfaceCapabilities2
				), "GetPhysicalDeviceSurfaceCapabilities2KHR", "Present mode compatibility: count");

				int numCompatiblePresentModes = presentModeCompatibility.presentModeCount();

				var compatiblePresentModeBuffer = stack.callocInt(numCompatiblePresentModes);
				presentModeCompatibility.pPresentModes(compatiblePresentModeBuffer);
				assertVkSuccess(vkGetPhysicalDeviceSurfaceCapabilities2KHR(
						instance.vkPhysicalDevice(), surfaceInfo, surfaceCapabilities2
				), "GetPhysicalDeviceSurfaceCapabilities2KHR", "Present mode compatibility: modes");

				for (int index = 0; index < numCompatiblePresentModes; index++) {
					int compatiblePresentMode = compatiblePresentModeBuffer.get(index);
					if (usedPresentModes.contains(compatiblePresentMode)) {
						compatibleUsedPresentModes.add(compatiblePresentMode);
					}
				}
			}

			if (instance.hasSwapchainMaintenance()) {
				var pPresentModes = stack.callocInt(compatibleUsedPresentModes.size());
				for (int compatiblePresentMode : compatibleUsedPresentModes) {
					pPresentModes.put(compatiblePresentMode);
				}
				pPresentModes.flip();

				var ciPresentModes = VkSwapchainPresentModesCreateInfoEXT.calloc(stack);
				ciPresentModes.sType$Default();
				ciPresentModes.pPresentModes(pPresentModes);

				ciSwapchain.pNext(ciPresentModes);
			}

			var pSwapchain = stack.callocLong(1);
			assertVkSuccess(vkCreateSwapchainKHR(
					instance.vkDevice(), ciSwapchain, null, pSwapchain
			), "CreateSwapchainKHR", null);
			long vkSwapchain = pSwapchain.get(0);

			currentSwapchainID += 1;
			instance.debug.name(stack, vkSwapchain, VK_OBJECT_TYPE_SWAPCHAIN_KHR, "Swapchain-" + title + currentSwapchainID);

			currentSwapchain = new VkbSwapchain(
					instance, vkSwapchain, title, cleaner, surfaceFormat, swapchainImageUsage, presentMode,
					width, height, presentFamily, compatibleUsedPresentModes
			);
		}
	}

	private void recreateSwapchain(int presentMode) {
		var oldSwapchain = currentSwapchain;

		if (windowLoop == null) createSwapchain(presentMode, true);
		else windowLoop.queueResize(() -> createSwapchain(presentMode, true), this);

		cleaner.onChangeCurrentSwapchain(oldSwapchain, currentSwapchain);
	}

	private void maybeRecreateSwapchain(int presentMode) {
		int oldWidth = width;
		int oldHeight = height;

		updateSize();

		boolean shouldRecreate = oldWidth != width || oldHeight != height;
		if (width == 0 || height == 0) shouldRecreate = false;

		if (shouldRecreate) {
			var oldSwapchain = currentSwapchain;
			createSwapchain(presentMode, false);
			cleaner.onChangeCurrentSwapchain(oldSwapchain, currentSwapchain);
		}
	}

	private AcquiredImage acquireSwapchainImage(int presentMode, boolean useAcquireFence) {
		if (!supportedPresentModes.contains(presentMode)) {
			throw new IllegalArgumentException("Unsupported present mode " + presentMode + ": supported are " + supportedPresentModes);
		}
		this.usedPresentModes.add(presentMode);

		if (windowLoop != null && windowLoop.shouldCheckResize(this)) {
			windowLoop.queueResize(() -> maybeRecreateSwapchain(presentMode), this);
		}

		if (currentSwapchain == null) recreateSwapchain(presentMode);
		if (currentSwapchain == null) return null;
		if (currentSwapchain.isOutdated()) recreateSwapchain(presentMode);
		if (currentSwapchain == null) return null;

		var acquiredImage = currentSwapchain.acquireImage(presentMode, width, height, useAcquireFence);
		if (acquiredImage == null) {
			recreateSwapchain(presentMode);
			if (currentSwapchain == null) return null;
			acquiredImage = currentSwapchain.acquireImage(presentMode, width, height, useAcquireFence);
		}

		return acquiredImage;
	}

	/**
	 * Presents a previously acquired swapchain image
	 * @param image The swapchain image
	 * @param renderSubmission An <i>AwaitableSubmission</i> corresponding to the last queue submission that renders
	 *                         onto the swapchain image. Hint: <i>VkbQueue.submit</i> returns an
	 *                         <i>AwaitableSubmission</i> if the fence is not <i>VK_NULL_HANDLE</i>
	 */
	public void presentSwapchainImage(AcquiredImage image, AwaitableSubmission renderSubmission) {
		image.swapchain.presentImage(image, Objects.requireNonNull(renderSubmission));
	}

	/**
	 * Destroys this window, its surface, and its swapchains (unless it has already been destroyed). It is safe to call
	 * this method more than once, and even from multiple threads at the same time, but no other method calls must be
	 * pending.<br>
	 *
	 * Note: if this window was created using `BoilerBuilder.addWindow`, this window will automatically be destroyed
	 * during `BoilerInstance.destroyInitialObjects`.<br>
	 *
	 * Note: if you are using a `(Simple)WindowRenderLoop` to manage this window, it will automatically be destroyed
	 * after <i>glfwWindowShouldClose</i> returns <i>true</i>.
	 */
	public synchronized void destroy() {
		if (hasBeenDestroyed) return;

		cleaner.destroyEverything();
		vkDestroySurfaceKHR(instance.vkInstance(), vkSurface, null);
		surfaceCapabilities.free();

		if (windowLoop != null) windowLoop.destroy(this);
		else glfwDestroyWindow(glfwWindow);

		hasBeenDestroyed = true;
	}
}
