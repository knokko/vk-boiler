package com.github.knokko.boiler.descriptors;

import com.github.knokko.boiler.buffer.VmaBuffer;
import com.github.knokko.boiler.instance.BoilerInstance;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.vulkan.VK10.*;

public class BoilerDescriptors {

    private final BoilerInstance instance;

    public BoilerDescriptors(BoilerInstance instance) {
        this.instance = instance;
    }

    public long createLayout(MemoryStack stack, VkDescriptorSetLayoutBinding.Buffer bindings, String name) {
        var ciLayout = VkDescriptorSetLayoutCreateInfo.calloc(stack);
        ciLayout.sType$Default();
        ciLayout.flags(0);
        ciLayout.pBindings(bindings);

        var pLayout = stack.callocLong(1);
        assertVkSuccess(vkCreateDescriptorSetLayout(
                instance.vkDevice(), ciLayout, null, pLayout
        ), "CreateDescriptorSetLayout", name);
        long layout = pLayout.get(0);

        instance.debug.name(stack, layout, VK_OBJECT_TYPE_DESCRIPTOR_SET_LAYOUT, name);
        return layout;
    }

    public long[] allocate(MemoryStack stack, int amount, long descriptorPool, String name, long... layouts) {
        var aiSets = VkDescriptorSetAllocateInfo.calloc(stack);
        aiSets.sType$Default();
        aiSets.descriptorPool(descriptorPool);
        aiSets.pSetLayouts(stack.longs(layouts));

        var pSets = stack.callocLong(amount);
        assertVkSuccess(vkAllocateDescriptorSets(
                instance.vkDevice(), aiSets, pSets
        ), "AllocateDescriptorSets", name);

        long[] results = new long[amount];
        for (int index = 0; index < amount; index++) {
            long set = pSets.get(index);
            instance.debug.name(stack, set, VK_OBJECT_TYPE_DESCRIPTOR_SET, name);
            results[index] = set;
        }
        return results;
    }

    @SuppressWarnings("resource")
    public VkDescriptorBufferInfo.Buffer bufferInfo(MemoryStack stack, VmaBuffer... buffers) {
        var descriptorBufferInfo = VkDescriptorBufferInfo.calloc(buffers.length, stack);
        for (int index = 0; index < buffers.length; index++) {
            descriptorBufferInfo.get(index).buffer(buffers[index].vkBuffer());
            descriptorBufferInfo.get(index).offset(0);
            descriptorBufferInfo.get(index).range(buffers[index].size());
        }

        return descriptorBufferInfo;
    }

    public void writeBuffer(
            MemoryStack stack, VkWriteDescriptorSet.Buffer descriptorWrites,
            long descriptorSet, int binding, int type, VmaBuffer buffer
    ) {
        var write = descriptorWrites.get(binding);
        write.sType$Default();
        write.dstSet(descriptorSet);
        write.dstBinding(binding);
        write.dstArrayElement(0);
        write.descriptorCount(1);
        write.descriptorType(type);
        write.pBufferInfo(instance.descriptors.bufferInfo(stack, buffer));
    }
}
