package com.github.knokko.boiler.commands;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.queues.VkbQueueFamily;
import com.github.knokko.boiler.synchronization.FenceSubmission;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.util.function.Consumer;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/**
 * A helper class to easily record and submit one-time-use commands. When you create an instance of this class, it will
 * <ol>
 *     <li>Create a command pool with the <i>VK_COMMAND_POOL_CREATE_TRANSIENT_BIT</i> flag.</li>
 *     <li>Allocate a command buffer.</li>
 * </ol>
 * When you call the submit method, it will
 * <ol>
 *     <li>
 *         If the instance has been used before,
 *         wait until the previous submission has finished, and reset the command pool.
 *     </li>
 *     <li>If validation is enabled, update the debug names of the command pool and command buffer.</li>
 *     <li>Call <i>vkBeginCommandBuffer</i> with the <i>VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT</i> flag.</li>
 *     <li>Run your callback.</li>
 *     <li>Call <i>vkEndCommandBuffer</i>.</li>
 *     <li>Borrow a <i>VkbFence</i> from the fence bank</li>
 *     <li>Submit the command buffer with the borrowed fence</li>
 *     <li>Return the <i>VkbFence</i> to the fence bank</li>
 *     <li>Return the <i>FenceSubmission</i> to the caller</li>
 * </ol>
 * When you call the destroy method, it will:
 * <ol>
 *     <li>Await the last submission (if applicable)</li>
 *     <li>Destroy the command pool</li>
 * </ol>
 * Thus, this class takes care of all the boilerplate, and the application only needs to handle the actual
 * <i>vkCmd**</i> commands.
 */
public class SingleTimeCommands {

	private final BoilerInstance instance;
	private final VkbQueueFamily queueFamily;
	private final long commandPool;
	private final VkCommandBuffer commandBuffer;

	private FenceSubmission lastSubmission;

	/**
	 * @param instance The <i>BoilerInstance</i>
	 * @param queueFamily The queue family to which command buffer will be submitted. This class will always use the
	 *                    first queue of the family.
	 */
	public SingleTimeCommands(BoilerInstance instance, VkbQueueFamily queueFamily) {
		this.instance = instance;
		this.queueFamily = queueFamily;

		this.commandPool = instance.commands.createPool(
				VK_COMMAND_POOL_CREATE_TRANSIENT_BIT, queueFamily.index(), "SingleTimeCommands"
		);
		this.commandBuffer = instance.commands.createPrimaryBuffers(commandPool, 1, "SingleTimeCommands")[0];
	}

	/**
	 * Creates an instance that will submit the command buffers to the first queue of the graphics queue family
	 */
	public SingleTimeCommands(BoilerInstance instance) {
		this(instance, instance.queueFamilies().graphics());
	}

	/**
	 * <ol>
	 *     <li>
	 *         If this method has been called before,
	 *         waits until the previous submission has finished, and reset the command pool.
	 *     </li>
	 *     <li>If validation is enabled, updates the debug names of the command pool and command buffer.</li>
	 *     <li>Calls <i>vkBeginCommandBuffer</i> with the <i>VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT</i> flag.</li>
	 *     <li>Runs your <i>recordCommands</i> callback.</li>
	 *     <li>Calls <i>vkEndCommandBuffer</i>.</li>
	 *     <li>Borrows a <i>VkbFence</i> from the fence bank</li>
	 *     <li>Submits the command buffer with the borrowed fence</li>
	 *     <li>Returns the <i>VkbFence</i> to the fence bank</li>
	 *     <li>Returns the <i>FenceSubmission</i> to the caller</li>
	 * </ol>
	 *
	 * <p>
	 * 		Note that this method does <b>not</b> wait until the submission has finished. If you want this, you need to call
	 * 		the <i>awaitCompletion()</i> method of the returned <i>FenceSubmission</i>.
	 * </p>
	 *
	 * Note that this method is <i>synchronized</i>, so you can safely call it from multiple threads. It could however
	 * slow the second thread down since it will be blocked until the submission of the first thread is finished.
	 * If this is a potential problem, considering using multiple instances of this class.
	 * @param context The debug name that will be given to the command pool/buffer, if validation is enabled
	 * @param recordCommands The callback that records the actual commands
	 * @return The command submission, which you can await.
	 */
	public synchronized FenceSubmission submit(String context, Consumer<CommandRecorder> recordCommands) {
		try (var stack = stackPush()) {
			if (lastSubmission != null) {
				lastSubmission.awaitCompletion();
				assertVkSuccess(vkResetCommandPool(
						instance.vkDevice(), commandPool, 0
				), "ResetCommandPool", context);
			}

			instance.debug.name(stack, commandPool, VK_OBJECT_TYPE_COMMAND_POOL, context);
			instance.debug.name(stack, commandBuffer.address(), VK_OBJECT_TYPE_COMMAND_BUFFER, context);

			var recorder = CommandRecorder.begin(
					commandBuffer, instance, stack,
					VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT, context
			);
			recordCommands.accept(recorder);
			recorder.end();

			var fence = instance.sync.fenceBank.borrowFence(false, context);
			lastSubmission = queueFamily.first().submit(commandBuffer, context, null, fence);
			instance.sync.fenceBank.returnFence(fence);
			return lastSubmission;
		}
	}

	/**
	 * Waits until the last command submission has finished (if applicable), and then destroys the command pool.
	 */
	public void destroy() {
		if (lastSubmission != null) lastSubmission.awaitCompletion();
		vkDestroyCommandPool(instance.vkDevice(), commandPool, null);
	}
}
