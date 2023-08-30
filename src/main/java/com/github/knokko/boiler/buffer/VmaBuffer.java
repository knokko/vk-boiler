package com.github.knokko.boiler.buffer;

import static org.lwjgl.util.vma.Vma.vmaDestroyBuffer;

public record VmaBuffer(long vkBuffer, long vmaAllocation, long size) {

    void destroy(long vmaAllocator) {
        vmaDestroyBuffer(vmaAllocator, vkBuffer, vmaAllocation);
    }
}
