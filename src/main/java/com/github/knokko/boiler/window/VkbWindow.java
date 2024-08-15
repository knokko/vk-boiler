package com.github.knokko.boiler.window;

import com.github.knokko.boiler.instance.BoilerInstance;
import com.github.knokko.boiler.queue.QueueFamily;
import com.github.knokko.boiler.sync.AwaitableSubmission;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;

import java.util.*;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static java.lang.Math.max;
import static org.lwjgl.glfw.GLFW.glfwDestroyWindow;
import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.VK_OBJECT_TYPE_SWAPCHAIN_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkCreateSwapchainKHR;
import static org.lwjgl.vulkan.VK10.*;

public class VkbWindow {

    private BoilerInstance instance;
    private final SwapchainCleaner cleaner;
    public final long glfwWindow;
    public final long vkSurface;
    public final Collection<Integer> supportedPresentModes;
    public final int surfaceFormat;
    public final int surfaceColorSpace;
    private final VkSurfaceCapabilitiesKHR surfaceCapabilities;
    public final int swapchainImageUsage;
    public final int swapchainCompositeAlpha;
    public final QueueFamily presentFamily;

    private int width, height;
    private VkbSwapchain currentSwapchain;
    private long currentSwapchainID;
    private final String title;

    private boolean hasBeenDestroyed;

    WindowLoop windowLoop;

    public VkbWindow(
            boolean hasSwapchainMaintenance, long glfwWindow, long vkSurface, Collection<Integer> supportedPresentModes,
            String title, int surfaceFormat, int surfaceColorSpace, VkSurfaceCapabilitiesKHR surfaceCapabilities,
            int swapchainImageUsage, int swapchainCompositeAlpha, QueueFamily presentFamily
    ) {
        if (hasSwapchainMaintenance) cleaner = new SwapchainMaintenanceCleaner();
        else cleaner = new LegacySwapchainCleaner();
        this.glfwWindow = glfwWindow;
        this.vkSurface = vkSurface;
        this.supportedPresentModes = List.copyOf(supportedPresentModes);
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

    public AcquiredImage acquireSwapchainImageWithFence(int presentMode) {
        return acquireSwapchainImage(presentMode, true);
    }

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

    public int getWidth() {
        return width;
    }

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

            var pSwapchain = stack.callocLong(1);
            assertVkSuccess(vkCreateSwapchainKHR(
                    instance.vkDevice(), ciSwapchain, null, pSwapchain
            ), "CreateSwapchainKHR", null);
            long vkSwapchain = pSwapchain.get(0);

            currentSwapchainID += 1;
            instance.debug.name(stack, vkSwapchain, VK_OBJECT_TYPE_SWAPCHAIN_KHR, "Swapchain-" + title + currentSwapchainID);

            currentSwapchain = new VkbSwapchain(instance, vkSwapchain, title, cleaner, presentMode, width, height, presentFamily);
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

    public void presentSwapchainImage(AcquiredImage image, AwaitableSubmission renderSubmission) {
        image.swapchain.presentImage(image, Objects.requireNonNull(renderSubmission));
    }

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
