package com.github.knokko.boiler.buffer;

import static org.lwjgl.util.vma.Vma.vmaDestroyBuffer;

public record MappedVmaBuffer(long vkBuffer, long vmaAllocation, long size, long hostAddress) {

    public void destroy(long vmaAllocator) {
        vmaDestroyBuffer(vmaAllocator, vkBuffer, vmaAllocation);
    }

    public VmaBuffer asBuffer() {
        return new VmaBuffer(vkBuffer, vmaAllocation, size);
    }
}
