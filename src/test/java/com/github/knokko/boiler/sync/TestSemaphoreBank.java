package com.github.knokko.boiler.sync;

import com.github.knokko.boiler.builder.BoilerBuilder;
import org.junit.jupiter.api.Test;

import static com.github.knokko.boiler.sync.TestFenceBank.contains;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.vulkan.VK10.VK_API_VERSION_1_0;

public class TestSemaphoreBank {

    @Test
    public void testBasic() {
        var instance = new BoilerBuilder(
                VK_API_VERSION_1_0, "TestSemaphoreBank", 1
        ).validation().forbidValidationErrors().build();

        var bank = instance.sync.semaphoreBank;
        long semaphore1 = bank.borrowSemaphore();
        long[] semaphores = bank.borrowSemaphores(5);

        assertFalse(contains(semaphores, semaphore1));

        bank.returnSemaphores(semaphores[3], semaphore1);

        long[] newSemaphores = bank.borrowSemaphores(3);
        assertTrue(contains(newSemaphores, semaphore1));
        assertTrue(contains(newSemaphores, semaphores[3]));
        assertFalse(contains(semaphores, newSemaphores[2]));

        bank.returnSemaphores(newSemaphores);
        bank.returnSemaphores(semaphores[0], semaphores[1], semaphores[2], semaphores[4]);

        instance.destroyInitialObjects();
    }
}
