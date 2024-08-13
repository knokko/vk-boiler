package com.github.knokko.boiler.window;

import com.github.knokko.boiler.instance.BoilerInstance;
import com.github.knokko.boiler.sync.VkbFence;

import java.util.HashMap;
import java.util.Map;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.vulkan.VK10.vkDeviceWaitIdle;

class LegacySwapchainCleaner implements SwapchainCleaner {

    private final BoilerInstance instance;
    private final Map<VkbSwapchain, Long> acquireCounter = new HashMap<>();

    LegacySwapchainCleaner(BoilerInstance instance) {
        this.instance = instance;
    }

    @Override
    public void onAcquire(VkbSwapchain swapchain) {
        long newAcquireCount = acquireCounter.get(swapchain) + 1;
        acquireCounter.put(swapchain, newAcquireCount);

        // TODO Destroy them at some point
    }

    @Override
    public void onChangeCurrentSwapchain(VkbSwapchain oldSwapchain, VkbSwapchain newSwapchain) {
        if (oldSwapchain != null && !acquireCounter.containsKey(oldSwapchain)) {
            throw new IllegalStateException("Missed the switch to swapchain " + oldSwapchain);
        }
        if (newSwapchain != null) {
            if (acquireCounter.containsKey(newSwapchain)) {
                throw new IllegalStateException("Swapchain used twice? " + newSwapchain);
            }
            acquireCounter.put(newSwapchain, 0L);
        }
    }

    @Override
    public VkbFence getPresentFence() {
        return null;
    }

    @Override
    public void destroyNow() {
        assertVkSuccess(vkDeviceWaitIdle(
                instance.vkDevice()
        ), "DeviceWaitIdle", "LegacySwapchainCleaner.destroyNow");
    }

}
