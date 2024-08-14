package com.github.knokko.boiler.window;

import com.github.knokko.boiler.sync.VkbFence;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPresentInfoKHR;

interface SwapchainCleaner {

    void onAcquire(AcquiredImage acquiredImage);

    void onChangeCurrentSwapchain(VkbSwapchain oldSwapchain, VkbSwapchain newSwapchain);

    VkbFence getPresentFence();

    void beforePresent(MemoryStack stack, VkPresentInfoKHR presentInfo, AcquiredImage acquiredImage);

    void destroyNow();
}
