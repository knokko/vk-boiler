package com.github.knokko.boiler.window;

import com.github.knokko.boiler.synchronization.DummyFence;
import com.github.knokko.boiler.synchronization.FenceSubmission;
import com.github.knokko.boiler.synchronization.VkbFence;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestPresentationFinishedTracker {

	@Test
	public void testWithoutOldSwapchainsWithoutSwapchainMaintenance() {
		PresentationFinishedTracker tracker = new PresentationFinishedTracker(3, false);
		Random rng = new Random(12345);
		for (int counter = 0; counter < 100; counter++) {
			assertFalse(tracker.needsAcquireFence(false));
			assertFalse(tracker.needsPresentFence(rng.nextInt(3), false));
			assertFalse(tracker.hasFinishedAtLeastOnePresentation());
		}
	}

	@Test
	public void testWithoutOldSwapchainsWithSwapchainMaintenance() {
		PresentationFinishedTracker tracker = new PresentationFinishedTracker(2, true);
		Random rng = new Random(12345);
		for (int counter = 0; counter < 100; counter++) {
			assertFalse(tracker.needsAcquireFence(false));
			assertFalse(tracker.needsPresentFence(rng.nextInt(2), false));
			assertFalse(tracker.hasFinishedAtLeastOnePresentation());
		}
	}

	@Test
	public void testWithOldSwapchainsWithoutSwapchainMaintenance() {
		PresentationFinishedTracker tracker = new PresentationFinishedTracker(2, false);
		VkbFence wrongFence = DummyFence.create(false);
		FenceSubmission wrongSubmission = new FenceSubmission(wrongFence);
		VkbFence rightFence = DummyFence.create(false);
		FenceSubmission rightSubmission = new FenceSubmission(rightFence);

		// During frame 1, there are no previous acquires
		assertFalse(tracker.needsAcquireFence(true));
		assertFalse(tracker.needsPresentFence(1, true));
		assertFalse(tracker.hasFinishedAtLeastOnePresentation());

		// During frame 2, we don't know before acquiring whether we will re-acquire image 1
		assertTrue(tracker.needsAcquireFence(true));

		// Since we acquire image 0, we are not re-acquiring image 1, so this fence should not be used
		tracker.useAcquireFence(0, wrongSubmission);
		assertFalse(tracker.needsPresentFence(0, true));
		assertFalse(tracker.hasFinishedAtLeastOnePresentation());
		wrongFence.signal();
		assertFalse(tracker.hasFinishedAtLeastOnePresentation());

		// During frame 3, we will certainly re-acquire an image
		assertTrue(tracker.needsAcquireFence(true));
		tracker.useAcquireFence(1, rightSubmission);
		assertFalse(tracker.needsPresentFence(1, true));
		assertFalse(tracker.hasFinishedAtLeastOnePresentation());

		Random rng = new Random(1234);
		for (int counter = 0; counter < 100; counter++) {

			// Since it already has a pending submission, it does NOT need a new acquire fence
			assertFalse(tracker.needsAcquireFence(true));
			assertFalse(tracker.needsPresentFence(rng.nextInt(2), true));

			// Since I haven't signaled `rightFence` yet, this should return `false`
			assertFalse(tracker.hasFinishedAtLeastOnePresentation());
		}

		rightFence.signal();
		assertTrue(tracker.hasFinishedAtLeastOnePresentation());

		for (int counter = 0; counter < 100; counter++) {

			// Since it already has a finished submission, it does NOT need a new acquire fence
			assertFalse(tracker.needsAcquireFence(true));
			assertFalse(tracker.needsPresentFence(rng.nextInt(2), true));

			// Since rightFence is signaled, this should return `true`
			assertTrue(tracker.hasFinishedAtLeastOnePresentation());
		}
	}

	@Test
	public void testWithOldSwapchainsWithSwapchainMaintenance() {
		PresentationFinishedTracker tracker = new PresentationFinishedTracker(4, true);
		VkbFence fence = DummyFence.create(false);

		assertFalse(tracker.needsAcquireFence(true));
		assertTrue(tracker.needsPresentFence(0, true));
		tracker.usePresentFence(fence);
		assertFalse(tracker.hasFinishedAtLeastOnePresentation());

		Random rng = new Random(123456);
		for (int counter = 0; counter < 100; counter++) {
			assertFalse(tracker.needsAcquireFence(true));
			assertFalse(tracker.needsPresentFence(rng.nextInt(4), true));
			assertFalse(tracker.hasFinishedAtLeastOnePresentation());
		}

		fence.signal();
		assertTrue(tracker.hasFinishedAtLeastOnePresentation());

		for (int counter = 0; counter < 100; counter++) {
			assertFalse(tracker.needsAcquireFence(true));
			assertFalse(tracker.needsPresentFence(rng.nextInt(4), true));
			assertTrue(tracker.hasFinishedAtLeastOnePresentation());
		}
	}
}
