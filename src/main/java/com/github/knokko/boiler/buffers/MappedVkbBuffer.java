package com.github.knokko.boiler.buffers;

/**
 * Represents a <i>VkBuffer</i> with the given size and optional <i>VmaAllocation</i>. The buffer must be stored in
 * host-visible memory, and must be mapped at <i>hostAddress</i> during its lifetime.
 */
public record MappedVkbBuffer(long vkBuffer, long vmaAllocation, long size, long hostAddress) implements VkbBuffer {
}
