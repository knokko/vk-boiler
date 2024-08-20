package com.github.knokko.boiler.queue;

import java.util.Collection;

public record QueueFamilies(
		VkbQueueFamily graphics, VkbQueueFamily compute, VkbQueueFamily transfer,
		VkbQueueFamily videoEncode, VkbQueueFamily videoDecode, Collection<VkbQueueFamily> allEnabledFamilies
) {
}
