package com.github.knokko.boiler.buffer;

public record MappedVmaBuffer(long vkBuffer, long vmaAllocation, long size, long hostAddress) implements VmaBuffer { }
