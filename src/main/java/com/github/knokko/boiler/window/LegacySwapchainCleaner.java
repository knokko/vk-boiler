package com.github.knokko.boiler.window;

import com.github.knokko.boiler.instance.BoilerInstance;
import com.github.knokko.boiler.sync.VkbFence;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPresentInfoKHR;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.vulkan.VK10.*;

class LegacySwapchainCleaner implements SwapchainCleaner {

    private final BoilerInstance instance;
    private final List<State> swapchains = new ArrayList<>();

    LegacySwapchainCleaner(BoilerInstance instance) {
        this.instance = instance;
    }

    @Override
    public void onAcquire(AcquiredImage acquiredImage) {
        if (swapchains.isEmpty()) {
            throw new IllegalStateException("Unexpected image acquire: call onChangeCurrentSwapchain first");
        }
        var lastState = swapchains.get(swapchains.size() - 1);
        if (acquiredImage.swapchain != lastState.swapchain) {
            throw new IllegalStateException("Unexpected swapchain at image acquire");
        }

        lastState.acquiredImages.add(acquiredImage);

        destroyOldResources();
    }

    private void destroyOldResources() {
        for (int swapchainIndex = swapchains.size() - 1; swapchainIndex >= 0; swapchainIndex--) {
            var state = swapchains.get(swapchainIndex);

            boolean canDestroyOldSwapchains = false;
            var canDestroyOlderImages = new boolean[state.swapchain.images.length];

            List<AcquiredImage> remainingImages = new ArrayList<>(state.acquiredImages.size());
            for (int myImageIndex = state.acquiredImages.size() - 1; myImageIndex >= 0; myImageIndex--) {
                var image = state.acquiredImages.get(myImageIndex);
                if (canDestroyOlderImages[image.index()]) {
                    destroyImageNow(image, true);
                } else {
                    remainingImages.add(image);
                    if (image.acquireFence.isSignaled()) {
                        canDestroyOldSwapchains = true;
                        canDestroyOlderImages[image.index()] = true;
                    }
                }
            }

            state.acquiredImages.clear();
            Collections.reverse(remainingImages);
            state.acquiredImages.addAll(remainingImages);

            if (canDestroyOldSwapchains && swapchainIndex > 0) {
                System.out.println("Destroying " + swapchainIndex + " of the " + swapchains.size() + " swapchains");
                while (swapchainIndex > 0) {
                    swapchainIndex -= 1;
                    destroyStateNow(swapchains.get(swapchainIndex), true);
                }
                swapchains.clear();
                swapchains.add(state);
            }
        }
    }

    @Override
    public void onChangeCurrentSwapchain(VkbSwapchain oldSwapchain, VkbSwapchain newSwapchain) {
        if (!swapchains.isEmpty() && swapchains.get(swapchains.size() - 1).swapchain != oldSwapchain) {
            throw new IllegalStateException("Missed the switch to swapchain " + oldSwapchain);
        }
        if (newSwapchain != null) {
            if (swapchains.stream().anyMatch(state -> state.swapchain == newSwapchain)) {
                throw new IllegalStateException("Swapchain used twice? " + newSwapchain);
            }

            // Prevent swapchains from piling up when they are destroyed too quickly
            if (swapchains.size() > 5) destroyNow();

            swapchains.add(new State(newSwapchain, new ArrayList<>()));
        }
    }

    @Override
    public VkbFence getPresentFence() {
        return null;
    }

    @Override
    public void beforePresent(MemoryStack stack, VkPresentInfoKHR presentInfo, AcquiredImage acquiredImage) {
        //destroyOldResources();
        // TODO Maybe do something?
    }

    private void destroyImageNow(AcquiredImage image, boolean doSafetyChecks) {
        if (doSafetyChecks) {
            if (!image.acquireFence.isSignaled()) {
                throw new IllegalStateException("Acquire fence should be signaled by now!");
            }

            if (image.renderSubmission == null) {
                System.out.println("VkBoiler.LegacySwapchainCleaner: it looks like a acquired image was never presented: " +
                        "falling back to vkDeviceWaitIdle...");
                assertVkSuccess(vkDeviceWaitIdle(
                        instance.vkDevice()
                ), "DeviceWaitIdle", "LegacySwapchainCleaner.destroyImageNow");
            } else {
                image.renderSubmission.awaitCompletion();
            }
        }

        instance.sync.fenceBank.returnFence(image.acquireFence);
        if (image.acquireSemaphore != VK_NULL_HANDLE) {
            instance.sync.semaphoreBank.returnSemaphores(image.acquireSemaphore);
        }
        instance.sync.semaphoreBank.returnSemaphores(image.presentSemaphore());
    }

    private void destroyStateNow(State state, boolean doSafetyChecks) {
        for (var image : state.acquiredImages) destroyImageNow(image, doSafetyChecks);
        state.swapchain.destroyNow();
    }

    @Override
    public void destroyNow() {
        assertVkSuccess(vkDeviceWaitIdle(
                instance.vkDevice()
        ), "DeviceWaitIdle", "LegacySwapchainCleaner.destroyNow");

        for (var state : swapchains) destroyStateNow(state, false);
        swapchains.clear();
    }

    private record State(VkbSwapchain swapchain, List<AcquiredImage> acquiredImages) {}
}
