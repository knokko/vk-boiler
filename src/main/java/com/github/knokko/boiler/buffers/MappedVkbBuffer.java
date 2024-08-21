package com.github.knokko.boiler.buffers;

public record MappedVkbBuffer(long vkBuffer, long vmaAllocation, long size, long hostAddress) implements VkbBuffer {
}
