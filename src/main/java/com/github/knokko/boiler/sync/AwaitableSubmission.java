package com.github.knokko.boiler.sync;

public interface AwaitableSubmission {

	boolean hasCompleted();

	void awaitCompletion();
}
