package com.github.knokko.boiler.window;

import com.github.knokko.boiler.synchronization.AwaitableSubmission;
import com.github.knokko.boiler.synchronization.FenceSubmission;
import com.github.knokko.boiler.synchronization.VkbFence;

class PresentationFinishedTracker {

	private final boolean[] hasAcquired;
	private final boolean hasSwapchainMaintenance;

	private boolean hasAcquiredAnything;
	private AwaitableSubmission finished;

	PresentationFinishedTracker(int numImages, boolean hasSwapchainMaintenance) {
		this.hasAcquired = new boolean[numImages];
		this.hasSwapchainMaintenance = hasSwapchainMaintenance;
	}

	boolean needsAcquireFence(boolean hasOldSwapchains) {
		return !hasSwapchainMaintenance && finished == null && hasAcquiredAnything && hasOldSwapchains;
	}

	void useAcquireFence(int imageIndex, FenceSubmission acquireSubmission) {
		if (hasAcquired[imageIndex]) finished = acquireSubmission;
	}

	boolean needsPresentFence(int imageIndex, boolean hasOldSwapchains) {
		hasAcquired[imageIndex] = true;
		hasAcquiredAnything = true;
		return hasSwapchainMaintenance && finished == null && hasOldSwapchains;
	}

	void usePresentFence(VkbFence presentFence) {
		finished = new FenceSubmission(presentFence);
	}

	boolean hasFinishedAtLeastOnePresentation() {
		return finished != null && finished.hasCompleted();
	}
}
