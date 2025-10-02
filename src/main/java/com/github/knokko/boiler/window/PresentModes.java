package com.github.knokko.boiler.window;

import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

class PresentModes {

	final Set<Integer> supported;
	final Set<Integer> compatible = new HashSet<>();
	final Set<Integer> used = new HashSet<>();

	int current = -1;

	PresentModes(Collection<Integer> supported, Collection<Integer> requested) {
		this.supported = Set.copyOf(supported);
		this.used.addAll(requested);
		this.used.retainAll(this.supported);
	}

	private void checkSupported(int mode) {
		if (!supported.contains(mode)) {
			throw new IllegalArgumentException(
					"Unsupported present mode " + mode + ": supported present modes are " + supported
			);
		}
	}

	IntBuffer createSwapchain(MemoryStack stack, int presentMode, IntBuffer compatiblePresentModes) {
		checkSupported(presentMode);
		used.add(presentMode);
		current = presentMode;

		compatible.clear();
		compatible.add(presentMode);
		if (compatiblePresentModes != null) {
			for (int index = compatiblePresentModes.position(); index < compatiblePresentModes.limit(); index++) {
				int compatiblePresentMode = compatiblePresentModes.get(index);
				compatible.add(compatiblePresentMode);
			}
		}
		compatible.retainAll(used);

		IntBuffer pPresentModes = null;
		if (stack != null) {
			pPresentModes = stack.callocInt(compatible.size());
			for (int compatiblePresentMode : compatible) {
				pPresentModes.put(compatiblePresentMode);
			}
			pPresentModes.flip();
		}

		return pPresentModes;
	}

	boolean acquire(int mode) {
		checkSupported(mode);
		used.add(mode);
		return compatible.contains(mode);
	}

	boolean present(int mode) {
		if (mode != current) {
			checkSupported(mode);
			used.add(mode);
			return compatible.contains(mode);
		} else return false;
	}
}
