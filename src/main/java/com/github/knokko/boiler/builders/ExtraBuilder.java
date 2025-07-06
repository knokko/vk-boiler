package com.github.knokko.boiler.builders;

import com.github.knokko.boiler.BoilerExtra;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

class ExtraBuilder {
	final Set<String> layers = new HashSet<>();
	final Set<String> instanceExtensions = new HashSet<>();
	final Set<String> deviceExtensions = new HashSet<>();

	boolean swapchainMaintenance, memoryPriority, pageableMemory;

	BoilerExtra build() {
		return new BoilerExtra(
				Collections.unmodifiableSet(layers),
				Collections.unmodifiableSet(instanceExtensions),
				Collections.unmodifiableSet(deviceExtensions),
				swapchainMaintenance,
				memoryPriority,
				pageableMemory
		);
	}
}
