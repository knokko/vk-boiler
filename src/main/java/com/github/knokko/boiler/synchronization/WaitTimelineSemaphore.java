package com.github.knokko.boiler.synchronization;

/**
 * A simple tuple(vkSemaphore, waitDstStageMask, semaphoreValue) used as parameter in <i>VkbQueue.submit</i>
 * @param vkSemaphore The <i>VkSemaphore</i> to wait for
 * @param dstStageMask The corresponding element in <p>pWaitDstStageMasks</p>
 * @param value The counter value to wait for
 */
public record WaitTimelineSemaphore(long vkSemaphore, int dstStageMask, long value) {
}
