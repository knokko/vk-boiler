package com.github.knokko.boiler.utilities;

import org.junit.jupiter.api.Test;

import static com.github.knokko.boiler.utilities.BoilerMath.*;
import static com.github.knokko.boiler.utilities.CollectionHelper.createSet;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestBoilerMath {

	@Test
	public void testNextMultipleOfInt() {
		assertEquals(10, nextMultipleOf(10, 1));
		assertEquals(10, nextMultipleOf(10, 2));
		assertEquals(10, nextMultipleOf(10, 5));
		assertEquals(10, nextMultipleOf(10, 10));
		assertEquals(12, nextMultipleOf(10, 3));
		assertEquals(12, nextMultipleOf(10, 4));
	}

	@Test
	public void testNextMultipleOfLong() {
		assertEquals(10L, nextMultipleOf(10L, 1L));
		assertEquals(10L, nextMultipleOf(10L, 2L));
		assertEquals(10L, nextMultipleOf(10, 5L));
		assertEquals(10L, nextMultipleOf(10, 10L));
		assertEquals(12L, nextMultipleOf(10L, 3));
		assertEquals(12L, nextMultipleOf(10L, 4));
	}

	@Test
	public void testGreatestCommonDivisor() {
		assertEquals(1, greatestCommonDivisor(12, 13));
		assertEquals(2, greatestCommonDivisor(12, 14));
		assertEquals(5, greatestCommonDivisor(10, 5));
		assertEquals(5, greatestCommonDivisor(15, 10));
	}

	@Test
	public void testLeastCommonMultiple() {
		assertEquals(6L, leastCommonMultiple(createSet(2L, 3L)));
		assertEquals(12L, leastCommonMultiple(createSet(4L, 6L, 3L)));
		assertEquals(5L, leastCommonMultiple(createSet(5L)));
		assertEquals(1L, leastCommonMultiple(createSet()));
		assertEquals(10L, leastCommonMultiple(createSet(10L, 5L)));

		assertEquals(6L, leastCommonMultiple(2L, 3L, 2L));
		assertEquals(12L, leastCommonMultiple(4L, 6L, 3L, 6L));
		assertEquals(5L, leastCommonMultiple(5L, 5L));
		assertEquals(1L, leastCommonMultiple());
		assertEquals(10L, leastCommonMultiple(10L, 5L));
	}
}
