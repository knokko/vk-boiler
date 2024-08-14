package com.github.knokko.boiler.sync;

import com.github.knokko.boiler.instance.BoilerInstance;
import org.lwjgl.vulkan.VkFenceCreateInfo;

import java.util.concurrent.ConcurrentSkipListSet;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class FenceBank {

    private final BoilerInstance instance;
    private final ConcurrentSkipListSet<VkbFence> returnedFences = new ConcurrentSkipListSet<>();
    private final ConcurrentSkipListSet<VkbFence> borrowedFences = new ConcurrentSkipListSet<>();

    FenceBank(BoilerInstance instance) {
        this.instance = instance;
    }

    private synchronized VkbFence recycleFence() {
        var iterator = returnedFences.iterator();
        while (iterator.hasNext()) {
            var candidate = iterator.next();
            if (!candidate.isPending()) {
                iterator.remove();
                return candidate;
            }
        }
        return null;
    }

    public VkbFence borrowFence(boolean startSignaled, String name) {
        var fence = recycleFence();

        try (var stack = stackPush()) {
            if (fence == null) {
                var ciFence = VkFenceCreateInfo.calloc(stack);
                ciFence.sType$Default();
                ciFence.flags(startSignaled ? VK_FENCE_CREATE_SIGNALED_BIT : 0);

                var pFence = stack.callocLong(1);
                assertVkSuccess(vkCreateFence(
                        instance.vkDevice(), ciFence, null, pFence
                ), "CreateFence", name);
                fence = new VkbFence(instance, pFence.get(0), startSignaled);
            } else {
                if (startSignaled) fence.signal();
                else fence.reset();
            }

            fence.setName(name, stack);
        }

        borrowedFences.add(fence);
        return fence;
    }

    public VkbFence[] borrowFences(int amount, boolean startSignaled, String name) {
        var fences = new VkbFence[amount];
        for (int index = 0; index < amount; index++) {
            fences[index] = this.borrowFence(startSignaled, name + "-" + index);
        }
        return fences;
    }

    public void returnFence(VkbFence fence) {
        if (!borrowedFences.remove(fence)) {
            throw new IllegalArgumentException("This fence wasn't borrowed");
        }
        returnedFences.add(fence);
    }

    public void returnFences(VkbFence... fences) {
        for (VkbFence fence : fences) returnFence(fence);
    }

    /**
     * Note: this method is <b>not</b> thread-safe!
     * Do not call it while borrowing or returning fences!
     */
    public void awaitSubmittedFences() {
        for (var fence : borrowedFences) fence.waitIfSubmitted();
        for (var fence : returnedFences) fence.waitIfSubmitted();
    }

    /**
     * Note: this method is <b>not</b> thread-safe!
     * Do not call it while borrowing or returning fences!
     */
    public void destroy() {
        if (!borrowedFences.isEmpty()) {
            throw new IllegalStateException("Not all borrowed fences have been returned");
        }
        for (var fence : returnedFences) fence.destroy();
        returnedFences.clear();
    }
}
