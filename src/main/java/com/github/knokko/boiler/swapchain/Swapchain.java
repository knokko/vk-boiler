package com.github.knokko.boiler.swapchain;

import com.github.knokko.boiler.instance.BoilerInstance;

import java.util.ArrayList;
import java.util.Collection;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

class Swapchain {

    private final BoilerInstance instance;
    final long vkSwapchain;
    final SwapchainImage[] images;
    final int width, height, presentMode;
    final long[] acquireFences, acquireSemaphores, presentSemaphores;
    final Collection<Runnable> destructionCallbacks = new ArrayList<>();

    private int acquireIndex;
    private int outOfDateIndex = -1;
    private long acquireCounter;
    boolean canDestroyOldSwapchains;

    Swapchain(BoilerInstance instance, long vkSwapchain, int width, int height, int presentMode) {
        this.instance = instance;
        this.vkSwapchain = vkSwapchain;
        this.width = width;
        this.height = height;
        this.presentMode = presentMode;

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

            this.images = new SwapchainImage[numImages];
            this.acquireSemaphores = instance.sync.createSemaphores("AcquireSwapchainImage", numImages);
            this.acquireFences = instance.sync.createFences(true, numImages, "AcquireSwapchainImage");
            this.presentSemaphores = instance.sync.createSemaphores("PresentSwapchainImage", numImages);
            for (int index = 0; index < numImages; index++) {
                long vkImage = pImages.get(index);
                images[index] = new SwapchainImage(vkImage, index);
                instance.debug.name(stack, vkImage, VK_OBJECT_TYPE_IMAGE, "SwapchainImage" + index);
            }
        }
    }

    SwapchainImage acquire() {
        long acquireSemaphore = acquireSemaphores[acquireIndex];
        long acquireFence = acquireFences[acquireIndex];
        long presentSemaphore = presentSemaphores[acquireIndex];

        try (var stack = stackPush()) {
            instance.sync.waitAndReset(stack, acquireFence, 2_000_000_000L);
            if (acquireCounter > 2L * acquireFences.length) canDestroyOldSwapchains = true;

            var pImageIndex = stack.callocInt(1);
            int acquireResult = vkAcquireNextImageKHR(
                    instance.vkDevice(), vkSwapchain, 1_000_000_000,
                    acquireSemaphore, acquireFence, pImageIndex
            );

            if (acquireResult == VK_SUCCESS || acquireResult == VK_SUBOPTIMAL_KHR) {
                int imageIndex = pImageIndex.get(0);
                var image = images[imageIndex];
                image.acquireSemaphore = acquireSemaphore;
                image.acquireFence = acquireFence;
                image.presentSemaphore = presentSemaphore;

                acquireIndex = (acquireIndex + 1) % images.length;
                acquireCounter += 1;

                if (acquireResult == VK_SUCCESS) return image;
                else return null;
            } else outOfDateIndex = acquireIndex;

            if (acquireResult == VK_ERROR_OUT_OF_DATE_KHR) return null;
            else {
                assertVkSuccess(acquireResult, "AcquireNextImageKHR", null);
                throw new Error("This code should be unreachable");
            }
        }
    }

    void destroy() {
        try (var stack = stackPush()) {
            for (int index = 0; index < acquireFences.length; index++) {
                if (index != outOfDateIndex) {
                    assertVkSuccess(vkWaitForFences(
                            instance.vkDevice(), stack.longs(acquireFences[index]), true, 1_000_000_000
                    ), "WaitForFences", "SwapchainDestruction");
                }
            }
        }
        for (var callback : destructionCallbacks) callback.run();
        for (long fence : acquireFences) vkDestroyFence(instance.vkDevice(), fence, null);
        for (long semaphore : acquireSemaphores) vkDestroySemaphore(instance.vkDevice(), semaphore, null);
        for (long semaphore : presentSemaphores) vkDestroySemaphore(instance.vkDevice(), semaphore, null);
        vkDestroySwapchainKHR(instance.vkDevice(), vkSwapchain, null);
    }
}
