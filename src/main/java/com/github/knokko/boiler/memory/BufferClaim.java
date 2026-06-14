package com.github.knokko.boiler.memory;

import com.github.knokko.boiler.buffers.VkbBuffer;

class BufferClaim {

	final VkbBuffer buffer;
	final long alignment;

	int groupIndex;

	BufferClaim(VkbBuffer buffer, long alignment) {
		this.buffer = buffer;
		this.alignment = alignment;
	}
}
