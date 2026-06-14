package com.github.knokko.boiler.memory;

import com.github.knokko.boiler.buffers.MappedVkbBuffer;

import java.util.ArrayList;
import java.util.List;

import static com.github.knokko.boiler.utilities.BoilerMath.nextMultipleOf;

class BufferUsageClaims {

	final List<BufferClaim> claims = new ArrayList<>();
	final List<GroupedBufferClaims> groupedClaims = new ArrayList<>();

	void groupClaims(long maxSize) {
		long offset = 0L;
		for (BufferClaim claim : claims) {

			long oldSize = offset;
			offset = nextMultipleOf(offset, claim.alignment);
			claim.buffer.offset = offset;
			offset += claim.buffer.size;

			long newOffset = offset;
			if (newOffset > maxSize) {
				claim.buffer.offset = 0L;
				offset = claim.buffer.size;
				groupedClaims.add(new GroupedBufferClaims(oldSize));
			}

			claim.groupIndex = groupedClaims.size();
		}

		if (offset > 0L) groupedClaims.add(new GroupedBufferClaims(offset));

		for (var claim : claims) {
			if (claim.buffer instanceof MappedVkbBuffer) groupedClaims.get(claim.groupIndex).shouldMapMemory = true;
		}
	}

	void setBuffer(int groupIndex, long vkBuffer, long memorySize, long memoryAlignment) {
		for (BufferClaim claim : claims) {
			if (claim.groupIndex == groupIndex) {
				claim.buffer.vkBuffer = vkBuffer;
			}
		}
		groupedClaims.get(groupIndex).memorySize = memorySize;
		groupedClaims.get(groupIndex).memoryAlignment = memoryAlignment;
	}

	void setHostAddress(int groupIndex, long address) {
		for (BufferClaim claim : claims) {
			if (claim.groupIndex != groupIndex) continue;
			if (claim.buffer instanceof MappedVkbBuffer) {
				((MappedVkbBuffer) claim.buffer).hostAddress = address + groupedClaims.get(groupIndex).memoryOffset + claim.buffer.offset;
			}
		}
	}
}
