package com.github.knokko.boiler.sync;

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
