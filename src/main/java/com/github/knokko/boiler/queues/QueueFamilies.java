package com.github.knokko.boiler.queues;

import java.util.Collection;

public record QueueFamilies(
		VkbQueueFamily graphics, VkbQueueFamily compute, VkbQueueFamily transfer,
		VkbQueueFamily videoEncode, VkbQueueFamily videoDecode, Collection<VkbQueueFamily> allEnabledFamilies
) {
}
