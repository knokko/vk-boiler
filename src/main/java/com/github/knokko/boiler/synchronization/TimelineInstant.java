package com.github.knokko.boiler.synchronization;

public record TimelineInstant(VkbTimelineSemaphore semaphore, long value) implements AwaitableSubmission {

	@Override
	public boolean hasCompleted() {
		return semaphore.getValue() >= value;
	}

	@Override
	public void awaitCompletion() {
		semaphore.waitUntil(value);
	}
}
