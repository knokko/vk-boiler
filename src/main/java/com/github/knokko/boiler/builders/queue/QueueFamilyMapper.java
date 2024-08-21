package com.github.knokko.boiler.builders.queue;

import org.lwjgl.vulkan.VkQueueFamilyProperties;

import java.util.Set;

@FunctionalInterface
public interface QueueFamilyMapper {

	QueueFamilyMapping mapQueueFamilies(
			VkQueueFamilyProperties.Buffer queueFamilies,
			Set<String> deviceExtensions,
			boolean[][] presentSupportMatrix
	);
}
