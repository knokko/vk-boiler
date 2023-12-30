package com.github.knokko.boiler.swapchain;

import com.github.knokko.boiler.instance.BoilerInstance;
import com.github.knokko.boiler.sync.FatFence;

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
    final long[] acquireSemaphores, presentSemaphores;
    final FatFence[] acquireFences, presentFences;
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
            this.acquireSemaphores = instance.sync.semaphoreBank.borrowSemaphores(numImages);
            this.acquireFences = instance.sync.fenceBank.borrowSignaledFences(numImages);
            this.presentSemaphores = instance.sync.semaphoreBank.borrowSemaphores(numImages);
            if (instance.swapchains.hasSwapchainMaintenance) {
                this.presentFences = instance.sync.fenceBank.borrowSignaledFences(numImages);
            } else {
                this.presentFences = new FatFence[numImages];
            }
            for (int index = 0; index < numImages; index++) {
                long vkImage = pImages.get(index);
                images[index] = new SwapchainImage(vkImage, index);
                instance.debug.name(stack, vkImage, VK_OBJECT_TYPE_IMAGE, "SwapchainImage" + index);
            }
        }
    }

    SwapchainImage acquire() {
        long acquireSemaphore = acquireSemaphores[acquireIndex];
        FatFence acquireFence = acquireFences[acquireIndex];
        long presentSemaphore = presentSemaphores[acquireIndex];
        FatFence presentFence = presentFences[acquireIndex];

        try (var stack = stackPush()) {
            acquireFence.waitAndReset(instance, stack);
            if (acquireCounter > 2L * acquireFences.length) canDestroyOldSwapchains = true;

            var pImageIndex = stack.callocInt(1);
            int acquireResult = vkAcquireNextImageKHR(
                    instance.vkDevice(), vkSwapchain, instance.defaultTimeout,
                    acquireSemaphore, acquireFence.vkFence, pImageIndex
            );

            if (acquireResult == VK_SUCCESS || acquireResult == VK_SUBOPTIMAL_KHR) {
                int imageIndex = pImageIndex.get(0);
                var image = images[imageIndex];
                image.acquireSemaphore = acquireSemaphore;
                image.acquireFence = acquireFence;
                image.presentSemaphore = presentSemaphore;
                image.presentFence = presentFence;

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

    void destroy(boolean hasSwapchainMaintenance) {
        try (var stack = stackPush()) {
            for (int index = 0; index < acquireFences.length; index++) {
                if (index != outOfDateIndex) acquireFences[index].wait(instance, stack);
            }
        }
        for (var callback : destructionCallbacks) callback.run();
        instance.sync.fenceBank.returnFences(true, acquireFences);
        if (hasSwapchainMaintenance) instance.sync.fenceBank.returnFences(true, presentFences);
        instance.sync.semaphoreBank.returnSemaphores(acquireSemaphores);
        instance.sync.semaphoreBank.returnSemaphores(presentSemaphores);
        vkDestroySwapchainKHR(instance.vkDevice(), vkSwapchain, null);
    }
}
