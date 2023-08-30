package com.github.knokko.boiler.buffer;

import com.github.knokko.boiler.instance.BoilerInstance;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaAllocationInfo;
import org.lwjgl.vulkan.VkBufferCreateInfo;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVmaSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.util.vma.Vma.VMA_ALLOCATION_CREATE_MAPPED_BIT;
import static org.lwjgl.vulkan.VK10.*;

public class BoilerBuffers {

    private final BoilerInstance instance;

    public BoilerBuffers(BoilerInstance instance) {
        this.instance = instance;
    }

    public VmaBuffer create(long size, int usage, String name) {
        try (var stack = stackPush()) {
            var ciBuffer = VkBufferCreateInfo.calloc(stack);
            ciBuffer.sType$Default();
            ciBuffer.flags(0);
            ciBuffer.size(size);
            ciBuffer.usage(usage);
            ciBuffer.sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            var ciAllocation = VmaAllocationCreateInfo.calloc(stack);
            ciAllocation.usage(VMA_MEMORY_USAGE_AUTO);

            var pBuffer = stack.callocLong(1);
            var pAllocation = stack.callocPointer(1);

            assertVmaSuccess(vmaCreateBuffer(
                    instance.vmaAllocator(), ciBuffer, ciAllocation, pBuffer, pAllocation, null
            ), "CreateBuffer", name);
            instance.debug.name(stack, pBuffer.get(0), VK_OBJECT_TYPE_BUFFER, name);
            return new VmaBuffer(pBuffer.get(0), pAllocation.get(0), size);
        }
    }

    public MappedVmaBuffer createMapped(long size, int usage, String name) {
        try (var stack = stackPush()) {
            var ciBuffer = VkBufferCreateInfo.calloc(stack);
            ciBuffer.sType$Default();
            ciBuffer.flags(0);
            ciBuffer.size(size);
            ciBuffer.usage(usage);
            ciBuffer.sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            var ciAllocation = VmaAllocationCreateInfo.calloc(stack);
            ciAllocation.flags(
                    VMA_ALLOCATION_CREATE_HOST_ACCESS_RANDOM_BIT | VMA_ALLOCATION_CREATE_MAPPED_BIT
            );
            ciAllocation.usage(VMA_MEMORY_USAGE_AUTO);

            var pBuffer = stack.callocLong(1);
            var pAllocation = stack.callocPointer(1);
            var pInfo = VmaAllocationInfo.calloc(stack);

            assertVmaSuccess(vmaCreateBuffer(
                    instance.vmaAllocator(), ciBuffer, ciAllocation, pBuffer, pAllocation, pInfo
            ), "CreateBuffer", name);
            instance.debug.name(stack, pBuffer.get(0), VK_OBJECT_TYPE_BUFFER, name);
            return new MappedVmaBuffer(pBuffer.get(0), pAllocation.get(0), size, pInfo.pMappedData());
        }
    }
}
