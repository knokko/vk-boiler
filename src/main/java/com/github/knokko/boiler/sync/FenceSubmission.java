package com.github.knokko.boiler.sync;

public class FenceSubmission implements AwaitableSubmission {

	private final VkbFence fence;
	private final long referenceTime;

	public FenceSubmission(VkbFence fence) {
		this.fence = fence;
		this.referenceTime = fence.getCurrentTime();
	}

	@Override
	public boolean hasCompleted() {
		return fence.hasBeenSignaled(referenceTime);
	}

	@Override
	public void awaitCompletion() {
		fence.awaitSubmission(referenceTime);
	}
}
