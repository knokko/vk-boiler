package com.github.knokko.boiler.window;

import com.github.knokko.boiler.instance.BoilerInstance;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static java.lang.Math.max;
import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_OBJECT_TYPE_SWAPCHAIN_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkCreateSwapchainKHR;
import static org.lwjgl.vulkan.VK10.*;

public class VkbWindow {

    private final BoilerInstance instance;
    private final SwapchainCleaner cleaner;
    public final long glfwWindow;
    public final long vkSurface;

    private int width, height;
    private VkbSwapchain currentSwapchain;
    private long currentSwapchainID;

    public VkbWindow(BoilerInstance instance, boolean hasSwapchainMaintenance, long glfwWindow, long vkSurface) {
        this.instance = instance;
        if (hasSwapchainMaintenance) {
            throw new UnsupportedOperationException("TODO");
        } else {
            cleaner = new LegacySwapchainCleaner(instance);
        }
        this.glfwWindow = glfwWindow;
        this.vkSurface = vkSurface;

        this.updateSize();
    }

    public AcquiredImage acquireSwapchainImageWithFence(int presentMode) {
        return acquireSwapchainImage(presentMode, true);
    }

    public AcquiredImage acquireSwapchainImageWithSemaphore(int presentMode) {
        return acquireSwapchainImage(presentMode, false);
    }

    public void updateSize() {
        try (var stack = stackPush()) {
            var caps = instance.windowSurface().capabilities();
            assertVkSuccess(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(
                    instance.vkPhysicalDevice(), instance.windowSurface().vkSurface(), caps
            ), "GetPhysicalDeviceSurfaceCapabilitiesKHR", "Update window size");
            int width = caps.currentExtent().width();
            int height = caps.currentExtent().height();

            if (width == -1 || height == -1) {
                var pWidth = stack.callocInt(1);
                var pHeight = stack.callocInt(1);
                glfwGetFramebufferSize(instance.glfwWindow(), pWidth, pHeight);
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

    private VkbSwapchain createSwapchain(int presentMode, VkbSwapchain oldSwapchain) {
        try (var stack = stackPush()) {
            if (width == 0 || height == 0) return null;

            //noinspection resource
            var caps = instance.windowSurface().capabilities();

            int desiredImageCount = presentMode == VK_PRESENT_MODE_MAILBOX_KHR ? 3 : 2;
            var ciSwapchain = VkSwapchainCreateInfoKHR.calloc(stack);
            ciSwapchain.sType$Default();
            ciSwapchain.flags(0);
            ciSwapchain.surface(instance.windowSurface().vkSurface());
            ciSwapchain.minImageCount(max(desiredImageCount, caps.minImageCount()));
            ciSwapchain.imageFormat(instance.swapchainSettings.surfaceFormat().format());
            ciSwapchain.imageColorSpace(instance.swapchainSettings.surfaceFormat().colorSpace());
            ciSwapchain.imageExtent().set(width, height);
            ciSwapchain.imageArrayLayers(1);
            ciSwapchain.imageUsage(instance.swapchainSettings.imageUsage());

            if (instance.queueFamilies().graphics() == instance.queueFamilies().present()) {
                ciSwapchain.imageSharingMode(VK_SHARING_MODE_EXCLUSIVE);
            } else {
                ciSwapchain.imageSharingMode(VK_SHARING_MODE_CONCURRENT);
                ciSwapchain.queueFamilyIndexCount(2);
                ciSwapchain.pQueueFamilyIndices(stack.ints(
                        instance.queueFamilies().graphics().index(), instance.queueFamilies().present().index()
                ));
            }

            ciSwapchain.preTransform(caps.currentTransform());
            ciSwapchain.compositeAlpha(instance.swapchainSettings.compositeAlpha());
            ciSwapchain.presentMode(presentMode);
            ciSwapchain.clipped(true);
            ciSwapchain.oldSwapchain(oldSwapchain != null ? oldSwapchain.vkSwapchain : VK_NULL_HANDLE);

            var pSwapchain = stack.callocLong(1);
            assertVkSuccess(vkCreateSwapchainKHR(
                    instance.vkDevice(), ciSwapchain, null, pSwapchain
            ), "CreateSwapchainKHR", null);
            long vkSwapchain = pSwapchain.get(0);

            currentSwapchainID += 1;
            instance.debug.name(stack, vkSwapchain, VK_OBJECT_TYPE_SWAPCHAIN_KHR, "Swapchain" + currentSwapchainID);

            return new VkbSwapchain(instance, vkSwapchain, cleaner, presentMode, width, height);
        }
    }

    private void recreateSwapchain(int presentMode) {
        var oldSwapchain = currentSwapchain;
        currentSwapchain = createSwapchain(presentMode, oldSwapchain);
        cleaner.onChangeCurrentSwapchain(oldSwapchain, currentSwapchain);
    }

    private AcquiredImage acquireSwapchainImage(int presentMode, boolean useAcquireFence) {
        updateSize();
        if (width == 0 || height == 0) return null;

        if (currentSwapchain == null) recreateSwapchain(presentMode);
        if (currentSwapchain == null) return null;
        if (currentSwapchain.isOutdated()) recreateSwapchain(presentMode);
        if (currentSwapchain == null) return null;

        var acquiredImage = currentSwapchain.acquireImage(presentMode, width, height, useAcquireFence);
        if (acquiredImage == null) {
            updateSize();
            recreateSwapchain(presentMode);
            if (currentSwapchain == null) return null;
            acquiredImage = currentSwapchain.acquireImage(presentMode, width, height, useAcquireFence);
        }

        return acquiredImage;
    }

    public void presentSwapchainImage(AcquiredImage image) {
        // TODO Check whether the renderFinishedFence is required
        image.swapchain.presentImage(image);
    }

    public void destroy() {
        cleaner.destroyNow();
    }
}
