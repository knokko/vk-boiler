package com.github.knokko.boiler.descriptors;

import com.github.knokko.boiler.instance.BoilerInstance;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.BiConsumer;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/**
 * <p>
 *     A <i>GrowingDescriptorBank</i> is a 'bank' of descriptor sets in the sense that users can borrow descriptor sets
 *     from it, and return those once they are no longer needed. Once allocated, descriptor sets from the bank will
 *     never be freed or reset: when they are returned, they can simply be borrowed again. When the bank is destroyed,
 *     all descriptor pools will be destroyed.
 * </p>
 *
 * <p>
 *     Unlike <i>FixedDescriptorBank</i>s, <i>GrowingDescriptorBank</i>s have unlimited capacity: when all descriptor
 *     sets have been borrowed, it will create an additional (bigger) descriptor pool. But, just like fixed banks,
 *     growing banks also support only descriptor sets from 1 descriptor set layout.
 * </p>
 *
 * Borrowing and returning descriptor sets is thread-safe, but destroying the bank is not: the bank can only be
 * destroyed when all borrows and returns have been completed.
 */
public class GrowingDescriptorBank {

    private final BoilerInstance instance;
    private final long descriptorSetLayout;
    private final String name;
    private final BiConsumer<MemoryStack, VkDescriptorPoolCreateInfo> configureDescriptorPool;

    private final List<Long> descriptorPools = new ArrayList<>();
    private int nextCapacity = 2;


    private final ConcurrentSkipListSet<Long> unusedDescriptorSets, borrowedDescriptorSets;

    /**
     * @param name Debugging purposes only
     * @param configureDescriptorPool Populate the given <i>VkDescriptorPoolCreateInfo</i>. You should use this to
     *                                set <i>pPoolSizes</i> for a descriptor pool that can hold exactly <b>1</b>
     *                                descriptor set. (The <i>descriptorCount</i>s will automatically be multiplied
     *                                with the capacity of the descriptor pool.) You should ignore <i>maxSets</i> and
     *                                you can optionally set the <i>flags</i>.
     */
    public GrowingDescriptorBank(
            BoilerInstance instance, long descriptorSetLayout, String name,
            BiConsumer<MemoryStack, VkDescriptorPoolCreateInfo> configureDescriptorPool
    ) {
        this.instance = instance;
        this.descriptorSetLayout = descriptorSetLayout;
        this.name = name;
        this.configureDescriptorPool = configureDescriptorPool;
        this.unusedDescriptorSets = new ConcurrentSkipListSet<>();
        this.borrowedDescriptorSets = new ConcurrentSkipListSet<>();
    }

    public long borrowDescriptorSet() {
        Long maybeResult = unusedDescriptorSets.pollFirst();
        if (maybeResult == null) {
            synchronized (this) {

                // Ensure that it's not possible to create 2 new pools at the same time
                maybeResult = unusedDescriptorSets.pollFirst();
                if (maybeResult == null) {

                    try (var stack = stackPush()) {
                        var ciPool = VkDescriptorPoolCreateInfo.calloc(stack);
                        ciPool.sType$Default();
                        configureDescriptorPool.accept(stack, ciPool);
                        ciPool.maxSets(nextCapacity);
                        for (var poolSize : Objects.requireNonNull(ciPool.pPoolSizes())) {
                            poolSize.descriptorCount(nextCapacity * poolSize.descriptorCount());
                        }

                        var pPool = stack.callocLong(1);
                        assertVkSuccess(vkCreateDescriptorPool(
                                instance.vkDevice(), ciPool, null, pPool
                        ), "CreateDescriptorPool", "GrowingDescriptorBank-" + name + "-" + nextCapacity);
                        long newDescriptorPool = pPool.get(0);

                        descriptorPools.add(newDescriptorPool);

                        var pSetLayouts = stack.callocLong(nextCapacity);
                        for (int index = 0; index < nextCapacity; index++) {
                            pSetLayouts.put(index, descriptorSetLayout);
                        }
                        var aiSets = VkDescriptorSetAllocateInfo.calloc(stack);
                        aiSets.sType$Default();
                        aiSets.descriptorPool(newDescriptorPool);
                        aiSets.pSetLayouts(pSetLayouts);

                        var pSets = stack.callocLong(nextCapacity);
                        assertVkSuccess(vkAllocateDescriptorSets(
                                instance.vkDevice(), aiSets, pSets
                        ), "AllocateDescriptorSets", "GrowingDescriptorBank-" + name + "-" + nextCapacity);

                        maybeResult = pSets.get(0);
                        for (int index = 1; index < nextCapacity; index++) {
                            unusedDescriptorSets.add(pSets.get(index));
                        }
                        nextCapacity *= 2;
                    }
                }
            }
        }
        borrowedDescriptorSets.add(maybeResult);
        return maybeResult;
    }

    public void returnDescriptorSet(long descriptorSet) {
        if (!borrowedDescriptorSets.remove(descriptorSet)) {
            throw new IllegalArgumentException("Descriptor set " + descriptorSet + " wasn't borrowed");
        }
        unusedDescriptorSets.add(descriptorSet);
    }

    public void destroy(boolean checkBorrows) {
        if (checkBorrows && !borrowedDescriptorSets.isEmpty()) {
            throw new IllegalStateException("Not all borrowed descriptor sets have been returned");
        }
        for (long descriptorPool : descriptorPools) {
            vkDestroyDescriptorPool(instance.vkDevice(), descriptorPool, null);
        }
    }
}
