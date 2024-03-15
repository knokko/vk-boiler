package com.github.knokko.boiler.buffer;

public record DeviceOnlyVmaBuffer(long vkBuffer, long vmaAllocation, long size) implements VmaBuffer {
}
