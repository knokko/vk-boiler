package com.github.knokko.boiler.synchronization;

public class DummyFence {

	public static VkbFence create(boolean startSignaled) {
		return new VkbFence(null, 123L, startSignaled);
	}
}
