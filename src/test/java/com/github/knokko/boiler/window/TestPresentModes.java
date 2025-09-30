package com.github.knokko.boiler.window;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

public class TestPresentModes {

	@Test
	public void testWithoutSwapchainMaintenance() {
		Set<Integer> supported = new HashSet<>();
		PresentModes modes = new PresentModes(supported, prepared);
	}
}
