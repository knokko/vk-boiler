package com.github.knokko.boiler.window;

import com.github.knokko.boiler.commands.CommandRecorder;
import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.synchronization.AwaitableSubmission;
import com.github.knokko.boiler.synchronization.VkbFence;
import com.github.knokko.boiler.synchronization.WaitSemaphore;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.vulkan.VK10.*;

/**
 * A simple abstract subclass of <i>WindowRenderLoop</i> for simple single-threaded renderers. This class automatically
 * manages 1 command pool, command buffer, and fence per frame-in-flight (which most simple renderers need).
 */
public abstract class SimpleWindowRenderLoop extends WindowRenderLoop {

	private long[] commandPools;
	private VkCommandBuffer[] commandBuffers;
	private VkbFence[] commandFences;

	public SimpleWindowRenderLoop(VkbWindow window, int numFramesInFlight, boolean acquireSwapchainImageWithFence, int presentMode) {
		super(window, numFramesInFlight, acquireSwapchainImageWithFence, presentMode);
	}

	@Override
	protected void setup(BoilerInstance instance) {
		commandPools = instance.commands.createPools(
				VK_COMMAND_POOL_CREATE_TRANSIENT_BIT,
				instance.queueFamilies().graphics().index(),
				numFramesInFlight, "SimpleWindowRenderLoopPool"
		);
		commandBuffers = instance.commands.createPrimaryBufferPerPool("SimpleWindowRenderLoopBuffer", commandPools);
		commandFences = instance.sync.fenceBank.borrowFences(
				numFramesInFlight, true, "SimpleWindowRenderLoopCommandFence"
		);
	}

	@Override
	protected AwaitableSubmission renderFrame(
			MemoryStack stack, int frameIndex, AcquiredImage acquiredImage, BoilerInstance instance
	) {
		var fence = commandFences[frameIndex];
		fence.waitAndReset();

		assertVkSuccess(vkResetCommandPool(
				instance.vkDevice(), commandPools[frameIndex], 0
		), "ResetCommandPool", "SimpleWindowRenderLoop");

		var commandBuffer = commandBuffers[frameIndex];
		var recorder = CommandRecorder.begin(
				commandBuffer, instance, stack,
				VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT,
				"SimpleWindowRenderLoop"
		);
		var waitSemaphores = acquireSwapchainImageWithFence ? null : new WaitSemaphore[]{new WaitSemaphore(
				acquiredImage.acquireSemaphore(), VK_PIPELINE_STAGE_TRANSFER_BIT
		)};

		recordFrame(stack, recorder, acquiredImage, instance);
		recorder.end();

		return instance.queueFamilies().graphics().first().submit(
				commandBuffer, "Fill", waitSemaphores, fence, acquiredImage.presentSemaphore()
		);
	}

	/**
	 * Record all commands to render onto the acquired swapchain image. Do <b>not</b> call <i>recorder.end()</i>
	 * because that will automatically happen after this method returns.
	 * @param stack A <i>MemoryStack</i> onto which you can allocate structures that you need for rendering.
	 * @param recorder The <i>CommandRecorder</i> onto which you should record commands to render on the
	 *                 swapchain image.
	 * @param acquiredImage The acquired swapchain image
	 * @param instance The VkBoiler instance
	 */
	protected abstract void recordFrame(MemoryStack stack, CommandRecorder recorder, AcquiredImage acquiredImage, BoilerInstance instance);

	@Override
	protected void cleanUp(BoilerInstance instance) {
		for (var fence : commandFences) fence.waitIfSubmitted();
		instance.sync.fenceBank.returnFences(commandFences);
		for (var commandPool : commandPools) vkDestroyCommandPool(instance.vkDevice(), commandPool, null);
	}
}
