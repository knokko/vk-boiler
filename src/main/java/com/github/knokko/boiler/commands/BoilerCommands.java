package com.github.knokko.boiler.commands;

import com.github.knokko.boiler.BoilerInstance;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class BoilerCommands {

	private final BoilerInstance instance;

	/**
	 * This constructor is meant for internal use only. You should use <i>boilerInstance.commands</i> instead.
	 */
	public BoilerCommands(BoilerInstance instance) {
		this.instance = instance;
	}

	/**
	 * Creates a single command pool with the given flags and queue family index.
	 * @param flags The <i>VkCommandPoolCreateFlagBits</i>
	 * @param queueFamilyIndex The <i>queueFamilyIndex</i> that will be propagated to the <i>VkCommandPoolCreateInfo</i>
	 * @param name The debug name that will be given to the command pool, when validation is enabled
	 * @return The created command pool
	 */
	public long createPool(int flags, int queueFamilyIndex, String name) {
		return createPools(flags, queueFamilyIndex, 1, name)[0];
	}

	/**
	 * Creates <i>amount</i> command pools with the given flags and queue family index.
	 * @param flags The <i>VkCommandPoolCreateFlagBits</i>
	 * @param queueFamilyIndex The <i>queueFamilyIndex</i> that will be propagated to the <i>VkCommandPoolCreateInfo</i>
	 * @param amount The number of command pools that should be created
	 * @param name The debug name that will be given to the command pools, when validation is enabled
	 * @return The created command pools
	 */
	public long[] createPools(int flags, int queueFamilyIndex, int amount, String name) {
		try (var stack = stackPush()) {
			var ciCommandPool = VkCommandPoolCreateInfo.calloc(stack);
			ciCommandPool.sType$Default();
			ciCommandPool.flags(flags);
			ciCommandPool.queueFamilyIndex(queueFamilyIndex);

			var pCommandPool = stack.callocLong(1);

			long[] commandPools = new long[amount];
			for (int index = 0; index < amount; index++) {
				String nameSuffix = amount > 1 ? Integer.toString(amount) : "";
				assertVkSuccess(vkCreateCommandPool(
						instance.vkDevice(), ciCommandPool, null, pCommandPool
				), "CreateCommandPool", name + nameSuffix);
				commandPools[index] = pCommandPool.get(0);
				instance.debug.name(stack, pCommandPool.get(0), VK_OBJECT_TYPE_COMMAND_POOL, nameSuffix);
			}
			return commandPools;
		}
	}

	/**
	 * Allocate <i>amount</i> primary command buffers from the given command pool.
	 * @param commandPool The command pool from which the command buffers should be allocated
	 * @param amount The number of command buffers to be allocated
	 * @param name The debug name of the command buffers, when validation is enabled
	 * @return The allocated command buffers
	 */
	public VkCommandBuffer[] createPrimaryBuffers(long commandPool, int amount, String name) {
		try (var stack = stackPush()) {
			var aiCommandBuffer = VkCommandBufferAllocateInfo.calloc(stack);
			aiCommandBuffer.sType$Default();
			aiCommandBuffer.commandPool(commandPool);
			aiCommandBuffer.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY);
			aiCommandBuffer.commandBufferCount(amount);

			var pCommandBuffer = stack.callocPointer(amount);
			assertVkSuccess(vkAllocateCommandBuffers(
					instance.vkDevice(), aiCommandBuffer, pCommandBuffer
			), "AllocateCommandBuffers", name);
			var result = new VkCommandBuffer[amount];
			for (int index = 0; index < amount; index++) {
				result[index] = new VkCommandBuffer(pCommandBuffer.get(index), instance.vkDevice());
				instance.debug.name(stack, result[index].address(), VK_OBJECT_TYPE_COMMAND_BUFFER, name);
			}
			return result;
		}
	}

	/**
	 * Allocates 1 command buffer from each given command pool.
	 * @param name The debug name of the command buffers, when validation is enabled
	 * @param commandPools The command pools from which 1 command buffer should be allocated
	 * @return The allocated command buffers
	 */
	public VkCommandBuffer[] createPrimaryBufferPerPool(String name, long... commandPools) {
		try (var stack = stackPush()) {
			var result = new VkCommandBuffer[commandPools.length];

			var aiCommandBuffer = VkCommandBufferAllocateInfo.calloc(stack);
			aiCommandBuffer.sType$Default();
			aiCommandBuffer.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY);
			aiCommandBuffer.commandBufferCount(1);

			var pCommandBuffer = stack.callocPointer(1);

			for (int index = 0; index < commandPools.length; index++) {
				aiCommandBuffer.commandPool(commandPools[index]);
				assertVkSuccess(vkAllocateCommandBuffers(
						instance.vkDevice(), aiCommandBuffer, pCommandBuffer
				), "AllocateCommandBuffers", name);
				result[index] = new VkCommandBuffer(pCommandBuffer.get(0), instance.vkDevice());
				instance.debug.name(stack, result[index].address(), VK_OBJECT_TYPE_COMMAND_BUFFER, name + index);
			}

			return result;
		}
	}

	/**
	 * Calls <i>vkBeginCommandBuffer</i> to <i>begin</i> the given command buffer. Note that you don't need this method
	 * if you already use <i>CommandRecorder.begin</i>
	 * @param commandBuffer The command buffer
	 * @param stack The <i>MemoryStack</i> onto which the <i>VkCommandBufferBeginInfo</i> should be allocated
	 * @param context When <i>vkBeginCommandBuffer</i> fails, an exception will be thrown that includes <i>context</i>
	 *                in its message
	 */
	public void begin(VkCommandBuffer commandBuffer, MemoryStack stack, String context) {
		begin(commandBuffer, stack, 0, context);
	}

	/**
	 * Calls <i>vkBeginCommandBuffer</i> to <i>begin</i> the given command buffer. Note that you don't need this method
	 * if you already use <i>CommandRecorder.begin</i>
	 * @param commandBuffer The command buffer
	 * @param stack The <i>MemoryStack</i> onto which the <i>VkCommandBufferBeginInfo</i> should be allocated
	 * @param flags The <i>VkCommandBufferUsageFlags</i> that should be given to the <i>VkCommandBufferBeginInfo</i>
	 * @param context When <i>vkBeginCommandBuffer</i> fails, an exception will be thrown that includes <i>context</i>
	 *                in its message
	 */
	public void begin(VkCommandBuffer commandBuffer, MemoryStack stack, int flags, String context) {
		var biCommands = VkCommandBufferBeginInfo.calloc(stack);
		biCommands.sType$Default();
		biCommands.flags(flags);

		assertVkSuccess(vkBeginCommandBuffer(
				commandBuffer, biCommands
		), "BeginCommandBuffer", context);
	}
}
