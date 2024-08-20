package com.github.knokko.boiler.window;

import com.github.knokko.boiler.commands.CommandRecorder;
import com.github.knokko.boiler.instance.BoilerInstance;
import com.github.knokko.boiler.sync.AwaitableSubmission;
import com.github.knokko.boiler.sync.VkbFence;
import com.github.knokko.boiler.sync.WaitSemaphore;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.vulkan.VK10.*;

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
		var waitSemaphores = acquireSwapchainImageWithFence ? null : new WaitSemaphore[] { new WaitSemaphore(
				acquiredImage.acquireSemaphore(), VK_PIPELINE_STAGE_TRANSFER_BIT
		) };

		recordFrame(stack, recorder, acquiredImage, instance);
		recorder.end();

		return instance.queueFamilies().graphics().queues().get(0).submit(
                commandBuffer, "Fill", waitSemaphores, fence, acquiredImage.presentSemaphore()
        );
	}

	protected abstract void recordFrame(MemoryStack stack, CommandRecorder recorder, AcquiredImage acquiredImage, BoilerInstance instance);

	@Override
	protected void cleanUp(BoilerInstance instance) {
		for (var fence : commandFences) fence.waitIfSubmitted();
		instance.sync.fenceBank.returnFences(commandFences);
		for (var commandPool : commandPools) vkDestroyCommandPool(instance.vkDevice(), commandPool, null);
	}
}
