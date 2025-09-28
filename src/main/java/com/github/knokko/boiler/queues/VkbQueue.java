package com.github.knokko.boiler.queues;

import com.github.knokko.boiler.synchronization.*;
import org.lwjgl.vulkan.*;

import java.util.concurrent.locks.ReadWriteLock;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSwapchain.vkQueuePresentKHR;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.vkQueueSubmit;

/**
 * Wraps a <i>VkQueue</i>, and has some convenience methods. All methods are <b>synchronized</b> because Vulkan requires
 * access to <i>VkQueue</i>s to be externally synchronized.
 * @param vkQueue
 */
public record VkbQueue(VkQueue vkQueue, ReadWriteLock waitIdleLock) {

	/**
	 * This is a variant of the <i>submit</i> method, but without the timeline semaphore parameters.
	 * @param commandBuffer The command buffer to be submitted
	 * @param context When <i>vkQueueSubmit</i> doesn't return <i>VK_SUCCESS</i>, an exception will be thrown, which
	 *                will contain <i>context</i> in its error message
	 * @param waitSemaphores The <i>pWaitSemaphores</i>, possibly an empty array. Passing <b>null</b> has the same
	 *                       effect as passing an empty array.
	 * @param fence The <i>VkbFence</i> that should be signalled when the command completes, may be <b>null</b>
	 * @param vkSignalSemaphores The <i>pSignalSemaphores</i>, possibly an empty array. Passing <b>null</b> has the same
	 * 	                     effect as passing an empty array.
	 * @return When <i>fence</i> is not <b>null</b>, this will be a <i>FenceSubmission</i> that will be signalled when
	 * the command completes. When <i>fence</i> is <b>null</b>, this method will return <b>null</b>.
	 */
	public FenceSubmission submit(
			VkCommandBuffer commandBuffer, String context,
			WaitSemaphore[] waitSemaphores, VkbFence fence, long... vkSignalSemaphores
	) {
		return submit(commandBuffer, context, waitSemaphores, fence, vkSignalSemaphores, null);
	}

	/**
	 * Submits a single command buffer via <i>vkQueueSubmit</i> using the given parameters
	 * @param commandBuffer The command buffer to be submitted
	 * @param context When <i>vkQueueSubmit</i> doesn't return <i>VK_SUCCESS</i>, an exception will be thrown, which
	 *                will contain <i>context</i> in its error message
	 * @param waitSemaphores The <i>pWaitSemaphores</i>, possibly an empty array. Passing <b>null</b> has the same
	 *                       effect as passing an empty array.
	 * @param fence The <i>VkbFence</i> that should be signalled when the command completes, may be <b>null</b>
	 * @param vkSignalSemaphores The <i>pSignalSemaphores</i>, possibly an empty array. Passing <b>null</b> has the same
	 * 	                     effect as passing an empty array.
	 * @param timelineWaits The timeline semaphores that must be signalled before the command can start, possibly empty
	 *                      or null.
	 * @param timelineSignals The timeline semaphores that will be signalled after the command completes, possibly
	 *                      empty or null.
	 * @return When <i>fence</i> is not <b>null</b>, this will be a <i>FenceSubmission</i> that will be signalled when
	 * the command completes. When <i>fence</i> is <b>null</b>, this method will return <b>null</b>.
	 */
	public FenceSubmission submit(
			VkCommandBuffer commandBuffer, String context,
			WaitSemaphore[] waitSemaphores, VkbFence fence, long[] vkSignalSemaphores,
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
					pSignalSemaphores.put(index, timelineSignals[index].semaphore().vkSemaphore);
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

			long fenceHandle = fence != null ? fence.getVkFenceAndSubmit() : VK_NULL_HANDLE;
			synchronized (this) {
				waitIdleLock.readLock().lock();
				try {
					assertVkSuccess(vkQueueSubmit(vkQueue, submission, fenceHandle), "QueueSubmit", context);
				} finally {
					waitIdleLock.readLock().unlock();
				}
			}

			return fence != null ? new FenceSubmission(fence) : null;
		}
	}

	/**
	 * Calls <i>vkQueuePresentKHR</i>, and returns the result
	 */
	public int present(VkPresentInfoKHR presentInfo) {
		synchronized (this) {
			waitIdleLock.readLock().lock();
			try {
				return vkQueuePresentKHR(vkQueue, presentInfo);
			} finally {
				waitIdleLock.readLock().unlock();
			}
		}
	}
}
