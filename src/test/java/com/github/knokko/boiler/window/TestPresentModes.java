package com.github.knokko.boiler.window;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;
import java.util.HashSet;
import java.util.Set;

import static com.github.knokko.boiler.utilities.CollectionHelper.createSet;
import static org.junit.jupiter.api.Assertions.*;

public class TestPresentModes {

	@Test
	public void testWithoutSwapchainMaintenance() {
		Set<Integer> supported = createSet(5, 1);
		PresentModes modes = new PresentModes(supported, new HashSet<>());

		assertEquals(supported, modes.supported);
		assertNotSame(supported, modes.supported);
		assertEquals(0, modes.used.size());
		assertEquals(-1, modes.current);

		//noinspection DataFlowIssue
		assertThrows(Exception.class, () -> modes.supported.add(12));

		assertThrows(Exception.class, () -> modes.createSwapchain(null, 2, null));
		assertEquals(0, modes.compatible.size());
		modes.compatible.add(1);
		assertNull(modes.createSwapchain(null, 1, null));

		assertEquals(1, modes.current);
		assertEquals(createSet(1), modes.used);

		modes.acquire(1);
		assertFalse(modes.present(1));

		assertEquals(createSet(1), modes.used);
		modes.acquire(5);
		assertEquals(modes.supported, modes.used);
		assertFalse(modes.present(5));
		assertEquals(modes.supported, modes.used);

		assertThrows(Exception.class, () -> modes.acquire(3));
		assertThrows(Exception.class, () -> modes.present(3));
	}

	@Test
	public void testWithSwapchainMaintenance() {
		Set<Integer> supported = createSet(5, 1, 6, 2);
		PresentModes modes = new PresentModes(supported, createSet(2, 3));

		assertEquals(supported, modes.supported);
		assertEquals(createSet(2), modes.used);
		assertEquals(-1, modes.current);

		assertEquals(0, modes.compatible.size());
		assertNull(modes.createSwapchain(null, 6, IntBuffer.allocate(0)));

		assertEquals(createSet(6), modes.compatible);
		assertEquals(createSet(2, 6), modes.used);
		assertEquals(6, modes.current);

		modes.acquire(6);
		assertFalse(modes.present(5));

		assertEquals(createSet(2, 5, 6), modes.used);
		assertEquals(6, modes.current);

		try (MemoryStack stack = MemoryStack.stackPush()) {
			var additionalCompatible = modes.createSwapchain(stack, 5, stack.ints(1, 2));
			assertEquals(1, additionalCompatible.remaining());
			assertEquals(2, additionalCompatible.get());
		}

		assertEquals(5, modes.current);
		assertEquals(createSet(2, 5, 6), modes.used);
		assertEquals(createSet(2, 5), modes.compatible);

		modes.acquire(2);
		assertTrue(modes.present(2));

		try (MemoryStack stack = MemoryStack.stackPush()) {
			var additionalCompatible = modes.createSwapchain(stack, 1, stack.ints(6));
			assertEquals(1, additionalCompatible.remaining());
			assertEquals(6, additionalCompatible.get());
		}

		assertEquals(1, modes.current);
		assertEquals(createSet(1, 2, 5, 6), modes.used);
		assertEquals(createSet(1, 6), modes.compatible);
	}
}
