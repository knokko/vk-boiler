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
	 * Waits until the queue submission completes
	 */
	void awaitCompletion();
}
