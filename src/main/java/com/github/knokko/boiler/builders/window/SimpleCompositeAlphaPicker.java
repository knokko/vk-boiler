package com.github.knokko.boiler.builders.window;

public class SimpleCompositeAlphaPicker implements CompositeAlphaPicker {

	private final int[] preferred;

	public SimpleCompositeAlphaPicker(int... preferred) {
		this.preferred = preferred;
	}

	@Override
	public int chooseCompositeAlpha(int availableMask) {
		for (int candidate : preferred) {
			if ((availableMask & candidate) != 0) return candidate;
		}

		return Integer.lowestOneBit(availableMask);
	}
}
