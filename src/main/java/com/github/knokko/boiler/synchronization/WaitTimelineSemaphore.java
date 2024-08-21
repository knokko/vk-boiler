package com.github.knokko.boiler.synchronization;

public record WaitTimelineSemaphore(long vkSemaphore, int dstStageMask, long value) {
}
