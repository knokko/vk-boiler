package com.github.knokko.boiler.buffers;

public record DeviceVkbBuffer(long vkBuffer, long vmaAllocation, long size) implements VkbBuffer {
}
