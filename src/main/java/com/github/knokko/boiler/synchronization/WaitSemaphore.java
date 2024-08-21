package com.github.knokko.boiler.synchronization;

public record WaitSemaphore(long vkSemaphore, int stageMask) {
}
