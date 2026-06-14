package com.github.knokko.boiler.memory;

class GroupedBufferClaims {

	final long expectedSize;

	boolean shouldMapMemory;
	long memorySize;
	long memoryAlignment;
	long memoryOffset;
	int allocationIndex;

	GroupedBufferClaims(long expectedSize) {
		this.expectedSize = expectedSize;
	}
}
