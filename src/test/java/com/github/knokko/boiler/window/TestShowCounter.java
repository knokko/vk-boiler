package com.github.knokko.boiler.window;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestShowCounter {

	@Test
	public void testNoHiddenFrames() {
		ShowCounter show = new ShowCounter(0);
		for (long counter = 0L; counter < 5000_000_000L; counter += 1L) {
			assertFalse(show.shouldShowNow());
		}
	}

	@Test
	public void testOneHiddenFrames() {
		ShowCounter show = new ShowCounter(1);
		assertTrue(show.shouldShowNow());
		for (int counter = 0; counter < 100; counter++) {
			assertFalse(show.shouldShowNow());
		}
	}

	@Test
	public void testTwoHiddenFrames() {
		ShowCounter show = new ShowCounter(2);
		assertFalse(show.shouldShowNow());
		assertTrue(show.shouldShowNow());
		for (int counter = 0; counter < 100; counter++) {
			assertFalse(show.shouldShowNow());
		}
	}

	@Test
	public void testFiveHiddenFrames() {
		ShowCounter show = new ShowCounter(5);
		assertFalse(show.shouldShowNow());
		assertFalse(show.shouldShowNow());
		assertFalse(show.shouldShowNow());
		assertFalse(show.shouldShowNow());
		assertTrue(show.shouldShowNow());
		for (int counter = 0; counter < 100; counter++) {
			assertFalse(show.shouldShowNow());
		}
	}
}

