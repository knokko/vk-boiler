package com.github.knokko.boiler.sync;

public record WaitSemaphore(long vkSemaphore, int stageMask) {
}
