package com.github.knokko.boiler.window;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static com.github.knokko.boiler.utilities.CollectionHelper.createSet;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;

public class TestPresentSemaphores {

	@Test
	public void testEmptyDestroy() {
		DummySwapchainFunctions functions = new DummySwapchainFunctions();
		PresentSemaphores semaphores = new PresentSemaphores(functions, "TestEmpty", 3);

		assertEquals(0, functions.borrowedSemaphores.size());
		semaphores.destroy();
	}

	@Test
	public void testPartialDestroy() {
		DummySwapchainFunctions functions = new DummySwapchainFunctions();
		PresentSemaphores semaphores = new PresentSemaphores(functions, "TestPartial", 3);

		long s1 = semaphores.get(1);
		assertNotEquals(VK_NULL_HANDLE, s1);
		assertEquals(createSet(s1), functions.borrowedSemaphores);

		for (int counter = 0; counter < 10; counter++) {
			assertEquals(s1, semaphores.get(1));
			assertEquals(createSet(s1), functions.borrowedSemaphores);
		}

		semaphores.destroy();
		assertEquals(0, functions.borrowedSemaphores.size());
	}

	@Test
	public void testUseThemAll() {
		DummySwapchainFunctions functions = new DummySwapchainFunctions();
		PresentSemaphores semaphores = new PresentSemaphores(functions, "TestAll", 5);

		long[] all = new long[5];
		all[3] = semaphores.get(3);
		all[1] = semaphores.get(1);
		all[4] = semaphores.get(4);
		all[0] = semaphores.get(0);
		all[2] = semaphores.get(2);

		Set<Long> set = new HashSet<>(5);
		for (long semaphore : all) set.add(semaphore);
		assertEquals(5, set.size());
		assertFalse(set.contains(VK_NULL_HANDLE));

		for (int counter = 0; counter < 100; counter++) {
			assertEquals(all[counter % 5], semaphores.get(counter % 5));
		}

		assertEquals(5, functions.borrowedSemaphores.size());
		semaphores.destroy();
		assertEquals(0, functions.borrowedSemaphores.size());
	}
}
