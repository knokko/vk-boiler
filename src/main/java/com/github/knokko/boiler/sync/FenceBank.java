package com.github.knokko.boiler.sync;

import com.github.knokko.boiler.instance.BoilerInstance;

import java.util.concurrent.ConcurrentSkipListSet;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class FenceBank {

    private final BoilerInstance instance;
    private final ConcurrentSkipListSet<Long> unusedFences = new ConcurrentSkipListSet<>();
    private final ConcurrentSkipListSet<Long> borrowedFences = new ConcurrentSkipListSet<>();

    FenceBank(BoilerInstance instance) {
        this.instance = instance;
    }

    public long borrowFence() {
        Long fence = unusedFences.pollFirst();
        if (fence == null) {
            fence = instance.sync.createFences(false, 1, "Borrowed")[0];
        }
        borrowedFences.add(fence);
        return fence;
    }

    public FatFence borrowSignaledFence() {
        return new FatFence(borrowFence(), true);
    }

    public long[] borrowFences(int amount) {
        long[] fences = new long[amount];
        for (int index = 0; index < amount; index++) fences[index] = this.borrowFence();
        return fences;
    }

    public FatFence[] borrowSignaledFences(int amount) {
        FatFence[] fences = new FatFence[amount];
        for (int index = 0; index < amount; index++) fences[index] = this.borrowSignaledFence();
        return fences;
    }

    public void returnFence(long fence, boolean mightNeedReset) {
        if (!borrowedFences.remove(fence)) {
            throw new IllegalArgumentException("This fence wasn't borrowed");
        }
        if (mightNeedReset) {
            assertVkSuccess(vkResetFences(instance.vkDevice(), fence), "ResetFences", "Bank return");
        }
        unusedFences.add(fence);
    }

    public void returnFences(boolean mightNeedReset, long... fences) {
        for (long fence : fences) {
            if (!borrowedFences.remove(fence)) {
                throw new IllegalArgumentException("This fence wasn't borrowed");
            }
        }

        if (mightNeedReset) {
            try (var stack = stackPush()) {
                assertVkSuccess(vkResetFences(
                        instance.vkDevice(), stack.longs(fences)
                ), "ResetFences", "Bank bulk return");
            }
        }

        for (long fence : fences) unusedFences.add(fence);
    }

    public void returnFences(boolean mightNeedReset, FatFence... fences) {
        long[] rawFences = new long[fences.length];
        for (int index = 0; index < fences.length; index++) rawFences[index] = fences[index].vkFence;
        returnFences(mightNeedReset, rawFences);
    }

    public void destroy() {
        if (!borrowedFences.isEmpty()) {
            throw new IllegalStateException("Not all borrowed fences have been returned");
        }
        for (long fence : unusedFences) {
            vkDestroyFence(instance.vkDevice(), fence, null);
        }
        unusedFences.clear();
    }
}
