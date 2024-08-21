package com.github.knokko.boiler.synchronization;

public interface AwaitableSubmission {

	boolean hasCompleted();

	void awaitCompletion();
}
