package com.github.knokko.boiler.queue;

import java.util.List;

public record VkbQueueFamily(int index, List<VkbQueue> queues) {
	// TODO Create convenience method to submit/present to the first queue
}
