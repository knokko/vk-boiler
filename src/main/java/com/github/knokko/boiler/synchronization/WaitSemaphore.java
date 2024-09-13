package com.github.knokko.boiler.synchronization;

/**
 * A tuple of (vkSemaphore, waitDstStageMask), used as parameter in <i>VkbQueue.submit</i>
 * @param vkSemaphore The <i>VkSemaphore</i> to wait for
 * @param stageMask The corresponding element in <i>pWaitDstStageMasks</i>
 */
public record WaitSemaphore(long vkSemaphore, int stageMask) {
}
