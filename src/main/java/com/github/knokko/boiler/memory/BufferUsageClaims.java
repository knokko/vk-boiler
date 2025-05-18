package com.github.knokko.boiler.memory;

import com.github.knokko.boiler.buffers.MappedVkbBuffer;

import java.util.ArrayList;
import java.util.List;

import static com.github.knokko.boiler.utilities.BoilerMath.nextMultipleOf;

class BufferUsageClaims {

	final List<BufferClaim> claims = new ArrayList<>();
	boolean shouldMapMemory;
	long memorySize;
	long memoryAlignment;
	long memoryOffset;

	long computeSize() {
		long offset = 0L;
		for (BufferClaim claim : claims) {
			offset = nextMultipleOf(offset, claim.alignment());
			claim.buffer().offset = offset;
			offset += claim.buffer().size;
			if (claim.buffer() instanceof MappedVkbBuffer) shouldMapMemory = true;
		}

		return offset;
	}

	void setBuffer(long vkBuffer, long memorySize, long memoryAlignment) {
		for (BufferClaim claim : claims) claim.buffer().vkBuffer = vkBuffer;
		this.memorySize = memorySize;
		this.memoryAlignment = memoryAlignment;
	}

	void setHostAddress(long address) {
		for (BufferClaim claim : claims) {
			if (claim.buffer() instanceof MappedVkbBuffer) {
				((MappedVkbBuffer) claim.buffer()).hostAddress = address + memoryOffset + claim.buffer().offset;
			}
		}
	}
}
