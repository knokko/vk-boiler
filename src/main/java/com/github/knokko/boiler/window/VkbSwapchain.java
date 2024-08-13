package com.github.knokko.boiler.window;

import com.github.knokko.boiler.instance.BoilerInstance;
import com.github.knokko.boiler.sync.VkbFence;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkSwapchainPresentFenceInfoEXT;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

class VkbSwapchain { // TODO Update README

    private final BoilerInstance instance;
    final long vkSwapchain;
    private final SwapchainCleaner cleaner;
    private final int presentMode; // TODO Support for switching present mode
    final int width, height;
    final VkbSwapchainImage[] images;

    final Collection<Runnable> destructionCallbacks = new ArrayList<>();


    private boolean outdated;

    VkbSwapchain(
            BoilerInstance instance, long vkSwapchain, SwapchainCleaner cleaner,
            int presentMode, int width, int height
    ) {
        this.instance = instance;
        this.vkSwapchain = vkSwapchain;
        this.cleaner = cleaner;

        this.presentMode = presentMode;
        this.width = width;
        this.height = height;

        try (var stack = stackPush()) {
            var pNumImages = stack.callocInt(1);
            assertVkSuccess(vkGetSwapchainImagesKHR(
                    instance.vkDevice(), vkSwapchain, pNumImages, null
            ), "GetSwapchainImagesKHR", "count");
            int numImages = pNumImages.get(0);

            var pImages = stack.callocLong(numImages);
            assertVkSuccess(vkGetSwapchainImagesKHR(
                    instance.vkDevice(), vkSwapchain, pNumImages, pImages
            ), "GetSwapchainImagesKHR", "images");

            this.images = new VkbSwapchainImage[numImages];
            for (int index = 0; index < numImages; index++) {
                this.images[index] = new VkbSwapchainImage(pImages.get(index), index);
                this.instance.debug.name(stack, pImages.get(index), VK_OBJECT_TYPE_IMAGE, "SwapchainImage" + index);
            }
        }
    }

    public boolean isOutdated() {
        return outdated;
    }

    AcquiredImage acquireImage(int presentMode, int width, int height, boolean useAcquireFence) {
        if (presentMode != this.presentMode) outdated = true;
        if (width != this.width || height != this.height) outdated = true;
        if (outdated) return null;

        try (var stack = stackPush()) {
            VkbFence acquireFence;
            long acquireSemaphore;

            if (useAcquireFence) {
                acquireFence = instance.sync.fenceBank.borrowFence(false, "AcquireFence");
                acquireSemaphore = VK_NULL_HANDLE;
            } else {
                acquireFence = null;
                acquireSemaphore = instance.sync.semaphoreBank.borrowSemaphore("AcquireSemaphore");
            }

            var pImageIndex = stack.callocInt(1);
            int acquireResult = vkAcquireNextImageKHR(
                    instance.vkDevice(), vkSwapchain, instance.defaultTimeout,
                    acquireSemaphore, acquireFence == null ? VK_NULL_HANDLE : acquireFence.getVkFenceAndSubmit(), pImageIndex
            );

            if (acquireResult == VK_SUBOPTIMAL_KHR || acquireResult == VK_ERROR_OUT_OF_DATE_KHR) {
                outdated = true;
            }

            if (acquireResult == VK_SUCCESS || acquireResult == VK_SUBOPTIMAL_KHR) {
                int imageIndex = pImageIndex.get(0);
                var image = images[imageIndex];

                var presentSemaphore = instance.sync.semaphoreBank.borrowSemaphore("PresentSemaphore");
                var presentFence = cleaner.getPresentFence();
                AcquiredImage acquiredImage;
                if (useAcquireFence) {
                    acquiredImage = new AcquiredImage(this, image, acquireFence, presentSemaphore, presentFence);
                } else {
                    acquiredImage = new AcquiredImage(this, image, acquireSemaphore, presentSemaphore, presentFence);
                }

                cleaner.onAcquire(this);

                return acquiredImage;
            }

            if (acquireResult == VK_ERROR_OUT_OF_DATE_KHR) return null;
            else {
                assertVkSuccess(acquireResult, "AcquireNextImageKHR", null);
                throw new Error("This code should be unreachable");
            }
        }
    }

    void presentImage(AcquiredImage image) {
        try (var stack = stackPush()) {
            var presentInfo = VkPresentInfoKHR.calloc(stack);
            presentInfo.sType$Default();
            presentInfo.pWaitSemaphores(stack.longs(image.presentSemaphore()));
            presentInfo.swapchainCount(1);
            presentInfo.pSwapchains(stack.longs(vkSwapchain));
            presentInfo.pImageIndices(stack.ints(image.index()));
            presentInfo.pResults(stack.callocInt(1));
            // TODO Let the cleaner handle this
//            if (hasSwapchainMaintenance) {
//                acquired.presentFence().waitAndReset(instance, stack);
//                acquiredSwapchain.images[acquired.imageIndex()].didDrawingFinish = didDrawingFinish;
//
//                var fiPresent = VkSwapchainPresentFenceInfoEXT.calloc(stack);
//                fiPresent.sType$Default();
//                fiPresent.pFences(stack.longs(acquired.presentFence().vkFence));
//
//                presentInfo.pNext(fiPresent);
//            }

            // TODO Check why this is needed
            //if (beforePresentCallback != null) beforePresentCallback.accept(presentInfo);

            int presentResult = vkQueuePresentKHR(
                    instance.queueFamilies().present().queues().get(0).vkQueue(), presentInfo
            );
            if (presentResult == VK_ERROR_OUT_OF_DATE_KHR || presentResult == VK_SUBOPTIMAL_KHR) {
                outdated = true;
                return;
            }
            assertVkSuccess(presentResult, "QueuePresentKHR", null);
            assertVkSuccess(Objects.requireNonNull(presentInfo.pResults()).get(0), "QueuePresentKHR", null);
        }
    }

    void destroyNow() {
        // TODO Return fences and semaphores
        for (var callback : destructionCallbacks) callback.run();
        vkDestroySwapchainKHR(instance.vkDevice(), vkSwapchain, null);
    }
}
