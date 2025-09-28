package com.github.knokko.boiler.window;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

class PresentModes {

	final Set<Integer> supported;
	final Set<Integer> compatible = new HashSet<>();
	final Set<Integer> used = new HashSet<>();

	int current = -1;

	PresentModes(Collection<Integer> supported, Collection<Integer> preparedPresentModes) {
		this.supported = Set.copyOf(supported);
		this.used.addAll(preparedPresentModes);
	}

	private void checkSupported(int mode) {
		if (!supported.contains(mode)) {
			throw new IllegalArgumentException(
					"Unsupported present mode " + mode + ": supported present modes are " + supported
			);
		}
	}

	void createSwapchain(int initialMode) {
		checkSupported(initialMode);
		current = initialMode;
		used.add(initialMode);
	}

	void acquire(int mode) {
		checkSupported(mode);
		used.add(mode);
	}

	boolean present(int mode) {
		if (mode != current) {
			checkSupported(mode);
			used.add(mode);
			current = mode;
			return true;
		} else return false;
	}
}
