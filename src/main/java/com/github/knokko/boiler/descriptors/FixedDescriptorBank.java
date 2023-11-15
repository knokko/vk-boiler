package com.github.knokko.boiler.descriptors;

import com.github.knokko.boiler.instance.BoilerInstance;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;

import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.BiConsumer;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/**
 * <p>
 * A <i>FixedDescriptorBank</i> is a descriptor pool from which descriptor sets of one specific descriptor set layout
 * can be 'borrowed'. All descriptor sets are allocated right after creation of the descriptor pool, and are never
 * freed or reset. Instead, descriptor sets can be 'borrowed' and 'returned', after which they can be 'borrowed' again.
 * </p>
 *
 * <p>
 *     <i>FixedDescriptorBank</i>s are 'fixed' in the sense that they have a bounded capacity specified in the
 *     constructor, and can't grow any bigger. You can use <i>GrowingDescriptorBank</i> instead if you don't like this
 *     limitation.
 * </p>
 *
 * Borrowing and returning descriptor sets is thread-safe, but destroying the bank is not: the bank can only be
 * destroyed when all borrows and returns have been completed.
 */
public class FixedDescriptorBank {

    private final BoilerInstance instance;
    private final long descriptorPool;

    private final ConcurrentSkipListSet<Long> borrowedDescriptorSets;
    private final ConcurrentSkipListSet<Long> unusedDescriptorSets;

    public FixedDescriptorBank(
            BoilerInstance instance, long descriptorSetLayout, String context,
            BiConsumer<MemoryStack, VkDescriptorPoolCreateInfo> configureDescriptorPool
    ) {
        this.instance = instance;
        try (var stack = stackPush()) {
            var ciPool = VkDescriptorPoolCreateInfo.calloc(stack);
            ciPool.sType$Default();
            configureDescriptorPool.accept(stack, ciPool);
            int capacity = ciPool.maxSets();

            var pPool = stack.callocLong(1);
            assertVkSuccess(vkCreateDescriptorPool(
                    instance.vkDevice(), ciPool, null, pPool
            ), "CreateDescriptorPool", "DescriptorBank" + context);
            this.descriptorPool = pPool.get(0);

            var aiSets = VkDescriptorSetAllocateInfo.calloc(stack);
            aiSets.sType$Default();
            aiSets.descriptorPool(descriptorPool);
            aiSets.pSetLayouts(stack.longs(descriptorSetLayout));

            var pSets = stack.callocLong(capacity);
            assertVkSuccess(vkAllocateDescriptorSets(
                    instance.vkDevice(), aiSets, pSets
            ), "AllocateDescriptorSets", "DescriptorBank" + context);

            this.unusedDescriptorSets = new ConcurrentSkipListSet<>();
            for (int index = 0; index < capacity; index++) {
                this.unusedDescriptorSets.add(pSets.get(index));
            }
            this.borrowedDescriptorSets = new ConcurrentSkipListSet<>();
        }
    }

    /**
     * Note: this method returns null when all descriptor sets are currently borrowed.
     */
    public Long borrowDescriptorSet() {
        Long result = unusedDescriptorSets.pollFirst();
        if (result != null) borrowedDescriptorSets.add(result);
        return result;
    }

    public void returnDescriptorSet(long descriptorSet) {
        if (!borrowedDescriptorSets.remove(descriptorSet)) {
            throw new IllegalArgumentException(descriptorSet + " wasn't borrowed");
        }
        unusedDescriptorSets.add(descriptorSet);
    }

    public void destroy(boolean checkEmpty) {
        if (checkEmpty && !borrowedDescriptorSets.isEmpty()) {
            throw new IllegalStateException("Not all descriptor sets have been returned");
        }
        vkDestroyDescriptorPool(instance.vkDevice(), descriptorPool, null);
    }
}
