package com.github.knokko.boiler.synchronization;

/**
 * Represents a queue submission that can be awaited
 */
public interface AwaitableSubmission {

	/**
	 * @return true if and only if the corresponding queue submission has completed
	 */
	boolean hasCompleted();

	/**
	 * Waits until the queue submission completes, using the default timeout specified via
	 * {@link com.github.knokko.boiler.builders.BoilerBuilder#defaultTimeout(long)}.
	 */
	void awaitCompletion();

	/**
	 * Waits until the queue submission completes, using the given timeout
	 * @param timeout The timeout, in nanoseconds
	 */
	void awaitCompletion(long timeout);
}
