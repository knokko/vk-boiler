package com.github.knokko.boiler.window;

import com.github.knokko.boiler.instance.BoilerInstance;
import com.github.knokko.boiler.sync.VkbFence;

interface SwapchainCleaner {

    void onAcquire(VkbSwapchain swapchain);

    void onChangeCurrentSwapchain(VkbSwapchain oldSwapchain, VkbSwapchain newSwapchain);

    VkbFence getPresentFence();

    void destroyNow();
}
