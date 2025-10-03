package com.github.knokko.boiler.window;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static com.github.knokko.boiler.utilities.CollectionHelper.createSet;
import static java.lang.Math.min;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;

public class TestAcquireSemaphores {

	@Test
	public void testEmptyDestroy() {
		DummySwapchainFunctions functions = new DummySwapchainFunctions();
		AcquireSemaphores semaphores = new AcquireSemaphores(functions, "TestEmpty", 2);
		assertEquals(0, functions.borrowedSemaphores.size());
		semaphores.destroy();
		assertEquals(0, functions.borrowedSemaphores.size());
	}

	@Test
	public void testPartialDestroy() {
		DummySwapchainFunctions functions = new DummySwapchainFunctions();
		AcquireSemaphores semaphores = new AcquireSemaphores(functions, "TestPartial", 2);

		long semaphore = semaphores.next();
		assertNotEquals(VK_NULL_HANDLE, semaphore);
		assertEquals(createSet(semaphore), functions.borrowedSemaphores);

		semaphores.destroy();
		assertEquals(0, functions.borrowedSemaphores.size());
	}

	@Test
	public void testMultipleFrames() {
		DummySwapchainFunctions functions = new DummySwapchainFunctions();
		AcquireSemaphores semaphores = new AcquireSemaphores(functions, "MultipleFrames", 4);

		Set<Long> distinctSemaphores = new HashSet<>();
		for (int counter = 0; counter < 100; counter++) {
			assertEquals(min(counter, 4), distinctSemaphores.size());
			distinctSemaphores.add(semaphores.next());
		}

		assertEquals(4, functions.borrowedSemaphores.size());

		semaphores.destroy();
		assertEquals(4, distinctSemaphores.size());
		assertEquals(0, functions.borrowedSemaphores.size());
	}
}
