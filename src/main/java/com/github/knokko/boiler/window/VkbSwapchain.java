package com.github.knokko.boiler.window;

import com.github.knokko.boiler.instance.BoilerInstance;
import com.github.knokko.boiler.queue.QueueFamily;
import com.github.knokko.boiler.sync.AwaitableSubmission;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkSwapchainPresentModeInfoEXT;

import java.util.*;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

class VkbSwapchain { // TODO Update README

    private final BoilerInstance instance;
    final long vkSwapchain;
    private final SwapchainCleaner cleaner;
    private final Set<Integer> supportedPresentModes;
    private int presentMode;
    final int width, height;
    QueueFamily presentFamily;
    final long[] images;

    final Collection<Runnable> destructionCallbacks = new ArrayList<>();


    private boolean outdated;

    VkbSwapchain(
            BoilerInstance instance, long vkSwapchain, String title, SwapchainCleaner cleaner,
            int presentMode, int width, int height, QueueFamily presentFamily, Set<Integer> supportedPresentModes
    ) {
        this.instance = instance;
        this.vkSwapchain = vkSwapchain;
        this.cleaner = cleaner;

        this.presentMode = presentMode;
        this.supportedPresentModes = Collections.unmodifiableSet(supportedPresentModes);
        this.width = width;
        this.height = height;
        this.presentFamily = presentFamily;

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

            this.images = new long[numImages];
            for (int index = 0; index < numImages; index++) {
                this.images[index] = pImages.get(index);
                this.instance.debug.name(stack, this.images[index], VK_OBJECT_TYPE_IMAGE, "SwapchainImage-" + title + index);
            }
        }
    }

    public boolean isOutdated() {
        return outdated;
    }

    AcquiredImage acquireImage(int presentMode, int width, int height, boolean useAcquireFence) {
        if (!this.supportedPresentModes.contains(presentMode)) outdated = true;
        if (width != this.width || height != this.height) outdated = true;
        if (outdated) return null;

        try (var stack = stackPush()) {
            var acquireFence = instance.sync.fenceBank.borrowFence(false, "AcquireFence");
            long acquireSemaphore;

            if (useAcquireFence) {
                acquireSemaphore = VK_NULL_HANDLE;
            } else {
                acquireSemaphore = instance.sync.semaphoreBank.borrowSemaphore("AcquireSemaphore");
            }

            var pImageIndex = stack.callocInt(1);
            int acquireResult = vkAcquireNextImageKHR(
                    instance.vkDevice(), vkSwapchain, instance.defaultTimeout,
                    acquireSemaphore, acquireFence.getVkFenceAndSubmit(), pImageIndex
            );

            if (acquireResult == VK_SUBOPTIMAL_KHR || acquireResult == VK_ERROR_OUT_OF_DATE_KHR) {
                outdated = true;
            }

            if (acquireResult == VK_SUCCESS || acquireResult == VK_SUBOPTIMAL_KHR) {
                int imageIndex = pImageIndex.get(0);

                var presentSemaphore = instance.sync.semaphoreBank.borrowSemaphore("PresentSemaphore");
                var presentFence = cleaner.getPresentFence();
                AcquiredImage acquiredImage = new AcquiredImage(
                        this, imageIndex, acquireFence, acquireSemaphore,
                        presentSemaphore, presentFence, presentMode
                );

                cleaner.onAcquire(acquiredImage);

                return acquiredImage;
            }

            if (acquireResult == VK_ERROR_OUT_OF_DATE_KHR) return null;
            else {
                assertVkSuccess(acquireResult, "AcquireNextImageKHR", null);
                throw new Error("This code should be unreachable");
            }
        }
    }

    void presentImage(AcquiredImage image, AwaitableSubmission renderSubmission) {
        try (var stack = stackPush()) {
            var presentInfo = VkPresentInfoKHR.calloc(stack);
            presentInfo.sType$Default();
            presentInfo.pWaitSemaphores(stack.longs(image.presentSemaphore()));
            presentInfo.swapchainCount(1);
            presentInfo.pSwapchains(stack.longs(vkSwapchain));
            presentInfo.pImageIndices(stack.ints(image.index()));
            presentInfo.pResults(stack.callocInt(1));

            if (image.beforePresentCallback != null) image.beforePresentCallback.accept(presentInfo);

            if (image.presentMode != this.presentMode) {
                this.presentMode = image.presentMode;

                var changePresentMode = VkSwapchainPresentModeInfoEXT.calloc(stack);
                changePresentMode.sType$Default();
                changePresentMode.pPresentModes(stack.ints(image.presentMode));

                presentInfo.pNext(changePresentMode);
            }

            image.renderSubmission = renderSubmission;
            cleaner.beforePresent(stack, presentInfo, image);



            int presentResult = presentFamily.queues().get(0).present(presentInfo);
            if (presentResult == VK_ERROR_OUT_OF_DATE_KHR || presentResult == VK_SUBOPTIMAL_KHR) {
                outdated = true;
                return;
            }
            assertVkSuccess(presentResult, "QueuePresentKHR", null);
            assertVkSuccess(Objects.requireNonNull(presentInfo.pResults()).get(0), "QueuePresentKHR", null);
        }
    }

    void destroyNow() {
        for (var callback : destructionCallbacks) callback.run();
        vkDestroySwapchainKHR(instance.vkDevice(), vkSwapchain, null);
    }
}
