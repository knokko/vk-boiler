package com.github.knokko.boiler.buffer;

public record DeviceVkbBuffer(long vkBuffer, long vmaAllocation, long size) implements VkbBuffer {
}
