package com.github.knokko.boiler.utilities;

import org.junit.jupiter.api.Test;

import static com.github.knokko.boiler.utilities.BoilerMath.nextMultipleOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestBoilerMath {

	@Test
	public void testNextMultipleOf() {
		assertEquals(10, nextMultipleOf(10, 1));
		assertEquals(10, nextMultipleOf(10, 2));
		assertEquals(10, nextMultipleOf(10, 5));
		assertEquals(10, nextMultipleOf(10, 10));
		assertEquals(12, nextMultipleOf(10, 3));
		assertEquals(12, nextMultipleOf(10, 4));
	}
}
