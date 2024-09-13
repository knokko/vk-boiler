package com.github.knokko.boiler.buffers;

/**
 * Represents a <i>VkBuffer</i> with the given size, and an optional <i>VmaAllocation</i>. Don't expect the buffer to
 * be located in host-visible memory.
 */
public record DeviceVkbBuffer(long vkBuffer, long vmaAllocation, long size) implements VkbBuffer {
}
