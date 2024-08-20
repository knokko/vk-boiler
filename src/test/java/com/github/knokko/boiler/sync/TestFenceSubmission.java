package com.github.knokko.boiler.sync;

import com.github.knokko.boiler.builder.BoilerBuilder;
import com.github.knokko.boiler.BoilerInstance;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VkSubmitInfo;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static java.lang.Thread.sleep;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.VK_API_VERSION_1_0;
import static org.lwjgl.vulkan.VK10.vkQueueSubmit;
import static org.lwjgl.vulkan.VK12.VK_API_VERSION_1_2;

public class TestFenceSubmission {

	@Test
	public void testHasCompleted() throws InterruptedException {
		var boiler = new BoilerBuilder(
				VK_API_VERSION_1_0, "TestFenceSubmission", 1
		).validation().forbidValidationErrors().build();

		var fence0 = boiler.sync.fenceBank.borrowFence(false, "StartZero");
		var fence1 = boiler.sync.fenceBank.borrowFence(true, "StartOne");

		var submission0a = new FenceSubmission(fence0);
		var submission1a = new FenceSubmission(fence1);

		assertFalse(submission0a.hasCompleted());
		assertTrue(submission1a.hasCompleted());

		fence1.reset();
		assertTrue(submission1a.hasCompleted());
		var submission1b = new FenceSubmission(fence1);
		assertFalse(submission1b.hasCompleted());

		fence0.signal();
		assertTrue(submission0a.hasCompleted());
		fence0.reset();
		var submission0b = new FenceSubmission(fence0);
		assertTrue(submission0a.hasCompleted());
		assertFalse(submission0b.hasCompleted());

		fence0.signal();
		assertTrue(submission0a.hasCompleted());
		assertTrue(submission0b.hasCompleted());
		assertTrue(new FenceSubmission(fence0).hasCompleted());
		fence0.reset();
		assertTrue(submission0a.hasCompleted());
		assertTrue(submission0b.hasCompleted());
		var submission0c = new FenceSubmission(fence0);
		assertFalse(submission0c.hasCompleted());

		emptySubmission(boiler, fence0);
		sleep(100);
		assertTrue(submission0a.hasCompleted());
		assertTrue(submission0c.hasCompleted());
		fence0.waitAndReset();
		var submission0d = new FenceSubmission(fence0);
		assertTrue(submission0a.hasCompleted());
		assertTrue(submission0c.hasCompleted());
		assertFalse(submission0d.hasCompleted());

		emptySubmission(boiler, fence0);
		sleep(100);
		assertTrue(submission0a.hasCompleted());
		assertTrue(submission0b.hasCompleted());
		assertTrue(submission0c.hasCompleted());
		assertTrue(submission0d.hasCompleted());

		boiler.sync.fenceBank.returnFences(fence0, fence1);
		boiler.destroyInitialObjects();
	}

	@Test
	public void testAwaitCompletion() {
		var boiler = new BoilerBuilder(
				VK_API_VERSION_1_2, "TestAwaitCompletion", 1
		).validation().forbidValidationErrors().build();

		var fence0 = boiler.sync.fenceBank.borrowFence(false, "Start0");
		var fence1 = boiler.sync.fenceBank.borrowFence(true, "Start1");

		var submission0a = new FenceSubmission(fence0);
		var submission1a = new FenceSubmission(fence1);

		assertCannotAwait(submission0a);
		assertInstantAwait(submission1a);

		fence0.signal();
		fence1.reset();

		var submission0b = new FenceSubmission(fence0);
		var submission1b = new FenceSubmission(fence1);

		assertInstantAwait(submission0a);
		assertInstantAwait(submission1a);
		assertInstantAwait(submission0b);
		assertCannotAwait(submission1b);

		emptySubmission(boiler, fence1);
		assertInstantAwait(submission1b);

		boiler.sync.fenceBank.returnFences(fence0, fence1);
		boiler.destroyInitialObjects();
	}

	private void assertInstantAwait(FenceSubmission submission) {
		long startTime = System.nanoTime();
		submission.awaitCompletion();
		long passedTime = System.nanoTime() - startTime;
		assertTrue(passedTime <= 100_000_000, "Expected passed time (" + passedTime + ") to be at most 100ms");
	}

	private void assertCannotAwait(FenceSubmission submission) {
		assertEquals(assertThrows(
				IllegalStateException.class, submission::awaitCompletion
		).getMessage(), "Fence is not signaled, nor pending");
	}

	private void emptySubmission(BoilerInstance instance, VkbFence fence) {
		try (var stack = stackPush()) {
			var emptySubmitInfo = VkSubmitInfo.calloc(stack);
			emptySubmitInfo.sType$Default();

			assertVkSuccess(vkQueueSubmit(
					instance.queueFamilies().compute().queues().get(0).vkQueue(), emptySubmitInfo, fence.getVkFenceAndSubmit()
			), "QueueSubmit", "test");
		}
	}
}
