package com.github.knokko.boiler.buffer;

public record MappedVkbBuffer(long vkBuffer, long vmaAllocation, long size, long hostAddress) implements VkbBuffer {
}
