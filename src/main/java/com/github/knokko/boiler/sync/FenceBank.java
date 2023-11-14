package com.github.knokko.boiler.sync;

import com.github.knokko.boiler.instance.BoilerInstance;

import java.util.concurrent.ConcurrentSkipListSet;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.vulkan.VK10.vkDestroyFence;
import static org.lwjgl.vulkan.VK10.vkResetFences;

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

    public void returnFence(long fence, boolean mightNeedReset) {
        if (!borrowedFences.remove(fence)) {
            throw new IllegalArgumentException("This fence wasn't borrowed");
        }
        if (mightNeedReset) {
            assertVkSuccess(vkResetFences(instance.vkDevice(), fence), "ResetFences", "Bank return");
        }
        unusedFences.add(fence);
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
