package com.github.knokko.boiler.builder.queue;

import org.lwjgl.vulkan.VkQueueFamilyProperties;

@FunctionalInterface
public interface QueueFamilyMapper {

    QueueFamilyMapping mapQueueFamilies(VkQueueFamilyProperties.Buffer queueFamilies, boolean[] presentSupport);
}
