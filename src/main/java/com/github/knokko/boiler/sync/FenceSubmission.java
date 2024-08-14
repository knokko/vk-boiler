package com.github.knokko.boiler.sync;

import static org.lwjgl.system.MemoryStack.stackPush;

public class FenceSubmission implements AwaitableSubmission {

	private final VkbFence fence;
	private final long submissionTime;

	public FenceSubmission(VkbFence fence) {
		// TODO Unit test this
		this.fence = fence;
		this.submissionTime = fence.getSubmissionTime();
	}

	@Override
	public boolean hasCompleted() {
		return fence.hasBeenSignaled(submissionTime);
	}

	@Override
	public void awaitCompletion() {
		try (var stack = stackPush()) {
			fence.awaitSubmission(stack, submissionTime);
		}
	}
}
