package com.github.knokko.boiler.synchronization;

/**
 * Represents a current or future fence submission
 */
public class FenceSubmission implements AwaitableSubmission {

	private final VkbFence fence;
	private final long referenceTime;

	/**
	 * Constructs a <i>FenceSubmission</i> that will be considered as <b>completed</b> when the given fence has been
	 * signalled. When the fence is already signalled, this submission will immediately be completed.
	 */
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
