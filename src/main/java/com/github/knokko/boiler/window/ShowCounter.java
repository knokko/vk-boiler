package com.github.knokko.boiler.window;

class ShowCounter {

	private int remainingFrames;

	ShowCounter(int numHiddenFrames) {
		this.remainingFrames = numHiddenFrames;
	}

	boolean shouldShowNow() {
		if (remainingFrames >= 0) remainingFrames -= 1;
		return remainingFrames == 0;
	}
}

