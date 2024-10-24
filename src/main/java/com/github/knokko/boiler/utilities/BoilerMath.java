package com.github.knokko.boiler.utilities;

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
		return reverted;
	}
}
