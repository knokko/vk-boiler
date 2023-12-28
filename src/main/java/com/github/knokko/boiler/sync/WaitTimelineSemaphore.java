package com.github.knokko.boiler.sync;

public record WaitTimelineSemaphore(long vkSemaphore, int dstStageMask, long value) {
}
