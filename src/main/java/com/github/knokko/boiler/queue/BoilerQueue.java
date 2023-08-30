package com.github.knokko.boiler.queue;

import com.github.knokko.boiler.sync.WaitSemaphore;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSubmitInfo;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.vkQueueSubmit;

public record BoilerQueue(VkQueue vkQueue) {

    public synchronized void submit(
            VkCommandBuffer commandBuffer, String context,
            WaitSemaphore[] waitSemaphores, long fence, long... vkSignalSemaphores
    ) {
        try (var stack = stackPush()) {

            var submission = VkSubmitInfo.calloc(stack);
            submission.sType$Default();
            if (waitSemaphores.length > 0) {
                submission.waitSemaphoreCount(waitSemaphores.length);
                var pWaitSemaphores = stack.callocLong(waitSemaphores.length);
                var pWaitDstStageMasks = stack.callocInt(waitSemaphores.length);
                for (int index = 0; index < waitSemaphores.length; index++) {
                    var semaphore = waitSemaphores[index];
                    pWaitSemaphores.put(index, semaphore.vkSemaphore());
                    pWaitDstStageMasks.put(index, semaphore.stageMask());
                }
                submission.pWaitSemaphores(pWaitSemaphores);
                submission.pWaitDstStageMask(pWaitDstStageMasks);
            }
            submission.pCommandBuffers(stack.pointers(commandBuffer.address()));
            if (vkSignalSemaphores.length > 0) {
                var pSignalSemaphores = stack.callocLong(vkSignalSemaphores.length);
                for (int index = 0; index < vkSignalSemaphores.length; index++) {
                    pSignalSemaphores.put(index, vkSignalSemaphores[index]);
                }
                submission.pSignalSemaphores(pSignalSemaphores);
            }

            assertVkSuccess(vkQueueSubmit(vkQueue, submission, fence), "QueueSubmit", context);
        }
    }
}
