package com.github.knokko.boiler.utilities;

import java.util.HashSet;
import java.util.Set;

public class BoilerMath {

	/**
	 * @return The smallest integer multiple of <i>alignment</i> that is greater than or equal to <i>value</i>
	 * @throws IllegalArgumentException If either of the parameters is negative
	 */
	public static long nextMultipleOf(long value, long alignment) {
		if (value < 0L || alignment < 0L) throw new IllegalArgumentException("Both parameters must be non-negative");
		long quotient = value / alignment;
		long reverted = quotient * alignment;
		if (reverted < value) reverted += alignment;
		if (reverted < 0L) throw new IllegalArgumentException("Long overflow: " + value + " and " + alignment);
		return reverted;
	}

	/**
	 * @return The smallest integer multiple of <i>alignment</i> that is greater than or equal to <i>value</i>
	 * @throws IllegalArgumentException If either of the parameters is negative
	 */
	public static int nextMultipleOf(int value, int alignment) {
		if (value < 0 || alignment < 0) throw new IllegalArgumentException("Both parameters must be non-negative");
		int quotient = value / alignment;
		int reverted = quotient * alignment;
		if (reverted < value) reverted += alignment;
		if (reverted < 0) throw new IllegalArgumentException("Integer overflow: " + value + " and " + alignment);
		return reverted;
	}

	// Modified version of https://www.geeksforgeeks.org/lcm-of-given-array-elements/

	/**
	 * @return The greatest common divisor of the non-negative long integers {@code a} and {@code b}
	 */
	public static long greatestCommonDivisor(long a, long b) {
		if (b == 0L) return a;
		return greatestCommonDivisor(b, a % b);
	}

	/**
	 * @return The least common multiple of all the non-negative long integers in {@code numbers}, or 1 when
	 * {@code numbers} is empty.
	 */
	public static long leastCommonMultiple(Set<Long> numbers) {
		long lcm = 1;
		for (long number : numbers) {
			long gcd = greatestCommonDivisor(lcm, number);
			lcm = (lcm * number) / gcd;
		}
		return lcm;
	}

	/**
	 * @return The least common multiple of all the non-negative long integers in {@code numbers}, or 1 when
	 * {@code numbers} is empty.
	 */
	public static long leastCommonMultiple(long... numbers) {
		Set<Long> set = new HashSet<>(numbers.length);
		for (long number : numbers) set.add(number);
		return leastCommonMultiple(set);
	}
}
