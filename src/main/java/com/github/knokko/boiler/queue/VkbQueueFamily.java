package com.github.knokko.boiler.queue;

import java.util.List;

public record VkbQueueFamily(int index, List<VkbQueue> queues) {

	public VkbQueue first() {
		return queues.get(0);
	}
}
