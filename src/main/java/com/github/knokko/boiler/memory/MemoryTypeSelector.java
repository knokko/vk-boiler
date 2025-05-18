package com.github.knokko.boiler.memory;

import com.github.knokko.boiler.BoilerInstance;

@FunctionalInterface
public interface MemoryTypeSelector {

	int chooseMemoryType(BoilerInstance instance, int allowedMemoryTypeBits);
}
