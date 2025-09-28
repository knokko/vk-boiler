package com.github.knokko.boiler.synchronization;

public class DummyFence extends VkbFence {

	public DummyFence(boolean startSignaled) {
		super(null, 123L, startSignaled);
	}

	@Override
	public boolean isPending() {
		try {
			return super.isPending();
		} catch (NullPointerException hadToCheck) {
			return true;
		}
	}

	@Override
	public boolean hasBeenSignaled(long referenceTime) {
		try {
			return super.hasBeenSignaled(referenceTime);
		} catch (NullPointerException hadToCheck) {
			return false;
		}
	}

	@Override
	public boolean equals(Object other) {
		return this == other;
	}
}
