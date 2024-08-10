package com.github.knokko.boiler.queue;

import com.github.knokko.boiler.sync.TimelineInstant;
import com.github.knokko.boiler.sync.WaitSemaphore;
import com.github.knokko.boiler.sync.WaitTimelineSemaphore;
import org.lwjgl.vulkan.*;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.vkQueueSubmit;

public record BoilerQueue(VkQueue vkQueue) {

    public void submit(
            VkCommandBuffer commandBuffer, String context,
            WaitSemaphore[] waitSemaphores, long fence, long... vkSignalSemaphores
    ) {
        submit(commandBuffer, context, waitSemaphores, fence, vkSignalSemaphores, new WaitTimelineSemaphore[0]);
    }

    public synchronized void submit(
            VkCommandBuffer commandBuffer, String context,
            WaitSemaphore[] waitSemaphores, long fence, long[] vkSignalSemaphores,
            WaitTimelineSemaphore[] timelineWaits, TimelineInstant... timelineSignals
    ) {
        if (waitSemaphores == null) waitSemaphores = new WaitSemaphore[0];
        if (vkSignalSemaphores == null) vkSignalSemaphores = new long[0];
        if (timelineWaits == null) timelineWaits = new WaitTimelineSemaphore[0];

        try (var stack = stackPush()) {

            var submission = VkSubmitInfo.calloc(stack);
            submission.sType$Default();

            int numWaitSemaphores = waitSemaphores.length + timelineWaits.length;
            if (numWaitSemaphores > 0) {
                submission.waitSemaphoreCount(numWaitSemaphores);
                var pWaitSemaphores = stack.callocLong(numWaitSemaphores);
                var pWaitDstStageMasks = stack.callocInt(numWaitSemaphores);
                for (int index = 0; index < waitSemaphores.length; index++) {
                    var semaphore = waitSemaphores[index];
                    pWaitSemaphores.put(timelineWaits.length + index, semaphore.vkSemaphore());
                    pWaitDstStageMasks.put(timelineWaits.length + index, semaphore.stageMask());
                }
                for (int index = 0; index < timelineWaits.length; index++) {
                    var semaphore = timelineWaits[index];
                    pWaitSemaphores.put(index, semaphore.vkSemaphore());
                    pWaitDstStageMasks.put(index, semaphore.dstStageMask());
                }
                submission.pWaitSemaphores(pWaitSemaphores);
                submission.pWaitDstStageMask(pWaitDstStageMasks);
            }

            submission.pCommandBuffers(stack.pointers(commandBuffer.address()));

            int numSignalSemaphores = vkSignalSemaphores.length + timelineSignals.length;
            if (numSignalSemaphores > 0) {
                var pSignalSemaphores = stack.callocLong(numSignalSemaphores);
                for (int index = 0; index < vkSignalSemaphores.length; index++) {
                    pSignalSemaphores.put(timelineSignals.length + index, vkSignalSemaphores[index]);
                }
                for (int index = 0; index < timelineSignals.length; index++) {
                    pSignalSemaphores.put(index, timelineSignals[index].timelineSemaphore());
                }
                submission.pSignalSemaphores(pSignalSemaphores);
            }

            if (timelineWaits.length > 0 || timelineSignals.length > 0) {
                var timeline = VkTimelineSemaphoreSubmitInfo.calloc(stack);
                timeline.sType$Default();
                timeline.waitSemaphoreValueCount(numWaitSemaphores);
                if (numWaitSemaphores > 0) {
                    var pValues = stack.callocLong(numWaitSemaphores);
                    for (int index = 0; index < timelineWaits.length; index++) {
                        pValues.put(index, timelineWaits[index].value());
                    }
                    timeline.pWaitSemaphoreValues(pValues);
                }
                timeline.signalSemaphoreValueCount(numSignalSemaphores);
                if (numSignalSemaphores > 0) {
                    var pValues = stack.callocLong(numSignalSemaphores);
                    for (int index = 0; index < timelineSignals.length; index++) {
                        pValues.put(index, timelineSignals[index].value());
                    }
                    timeline.pSignalSemaphoreValues(pValues);
                }

                submission.pNext(timeline);
            }

            assertVkSuccess(vkQueueSubmit(vkQueue, submission, fence), "QueueSubmit", context);
        }
    }
}
