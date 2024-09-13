package com.github.knokko.boiler.synchronization;

/**
 * A simple tuple of a timeline semaphore with a corresponding value
 * @param semaphore The timeline semaphore
 * @param value The value that should be awaited or signalled
 */
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
