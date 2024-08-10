package com.github.knokko.boiler.sync;

import com.github.knokko.boiler.instance.BoilerInstance;

import java.util.concurrent.ConcurrentSkipListSet;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class SemaphoreBank {

    private final BoilerInstance instance;
    private final ConcurrentSkipListSet<Long> unusedSemaphores = new ConcurrentSkipListSet<>();
    private final ConcurrentSkipListSet<Long> borrowedSemaphores = new ConcurrentSkipListSet<>();

    SemaphoreBank(BoilerInstance instance) {
        this.instance = instance;
    }

    public long borrowSemaphore(String name) {
        Long semaphore = unusedSemaphores.pollFirst();
        if (semaphore == null) {
            semaphore = instance.sync.createSemaphores("Borrowed", 1)[0];
        }
        try (var stack = stackPush()) {
            instance.debug.name(stack, semaphore, VK_OBJECT_TYPE_SEMAPHORE, name);
        }
        borrowedSemaphores.add(semaphore);
        return semaphore;
    }

    public long[] borrowSemaphores(int amount, String name) {
        long[] semaphores = new long[amount];
        for (int index = 0; index < amount; index++) semaphores[index] = this.borrowSemaphore(name + "-" + index);
        return semaphores;
    }

    public void returnSemaphores(long... semaphores) {
        for (long semaphore : semaphores) {
            if (!borrowedSemaphores.remove(semaphore)) {
                throw new IllegalArgumentException("This semaphore wasn't borrowed");
            }
        }

        for (long semaphore : semaphores) unusedSemaphores.add(semaphore);
    }

    public void destroy() {
        if (!borrowedSemaphores.isEmpty()) {
            throw new IllegalStateException("Not all borrowed semaphores have been returned");
        }
        for (long semaphore : unusedSemaphores) {
            vkDestroySemaphore(instance.vkDevice(), semaphore, null);
        }
        unusedSemaphores.clear();
    }
}
