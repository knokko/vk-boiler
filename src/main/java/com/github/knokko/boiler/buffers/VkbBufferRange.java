package com.github.knokko.boiler.buffers;

/**
 * Represents a range/part of a <i>VkBuffer</i>
 * @param buffer The buffer
 * @param offset The offset into the buffer, in bytes
 * @param size The size of the range, in bytes
 */
public record VkbBufferRange(VkbBuffer buffer, long offset, long size) {
}
