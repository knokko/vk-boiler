package com.github.knokko.boiler.commands;

import com.github.knokko.boiler.instance.BoilerInstance;
import com.github.knokko.boiler.sync.ResourceUsage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class BoilerCommands {

    private final BoilerInstance instance;

    public BoilerCommands(BoilerInstance instance) {
        this.instance = instance;
    }

    public long createPool(int flags, int queueFamilyIndex, String name) {
        try (var stack = stackPush()) {
            var ciCommandPool = VkCommandPoolCreateInfo.calloc(stack);
            ciCommandPool.sType$Default();
            ciCommandPool.flags(flags);
            ciCommandPool.queueFamilyIndex(queueFamilyIndex);

            var pCommandPool = stack.callocLong(1);
            assertVkSuccess(vkCreateCommandPool(
                    instance.vkDevice(), ciCommandPool, null, pCommandPool
            ), "CreateCommandPool", name);
            instance.debug.name(stack, pCommandPool.get(0), VK_OBJECT_TYPE_COMMAND_POOL, name);
            return pCommandPool.get(0);
        }
    }

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

    public void begin(VkCommandBuffer commandBuffer, MemoryStack stack, String context) {
        var biCommands = VkCommandBufferBeginInfo.calloc(stack);
        biCommands.sType$Default();

        assertVkSuccess(vkBeginCommandBuffer(
                commandBuffer, biCommands
        ), "BeginCommandBuffer", context);
    }
}
