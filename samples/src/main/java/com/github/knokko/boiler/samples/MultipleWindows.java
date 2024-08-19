package com.github.knokko.boiler.samples;

import com.github.knokko.boiler.builder.BoilerBuilder;
import com.github.knokko.boiler.builder.WindowBuilder;
import com.github.knokko.boiler.commands.CommandRecorder;
import com.github.knokko.boiler.instance.BoilerInstance;
import com.github.knokko.boiler.pipelines.GraphicsPipelineBuilder;
import com.github.knokko.boiler.sync.ResourceUsage;
import com.github.knokko.boiler.sync.WaitSemaphore;
import com.github.knokko.boiler.window.SwapchainResourceManager;
import com.github.knokko.boiler.window.VkbWindow;
import com.github.knokko.boiler.window.WindowLoop;
import org.lwjgl.vulkan.VkClearColorValue;
import org.lwjgl.vulkan.VkPushConstantRange;
import org.lwjgl.vulkan.VkRenderingAttachmentInfo;

import java.util.Random;
import java.util.stream.IntStream;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static java.lang.Thread.sleep;
import static org.joml.Math.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_FIFO_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.VK_API_VERSION_1_2;

public class MultipleWindows {

    public static void main(String[] args) throws InterruptedException {
        var windows = new VkbWindow[2];

        var boiler = new BoilerBuilder(
                VK_API_VERSION_1_2, "MultipleWindowsDemo", 1
        )
                .validation()
                .enableDynamicRendering()
                .addWindow(new WindowBuilder(
                        800, 500, VK_IMAGE_USAGE_TRANSFER_DST_BIT
                ).callback(window -> windows[0] = window))
                .addWindow(new WindowBuilder(
                        800, 500, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT
                ).callback(window -> windows[1] = window))
                .build();

        var fillWindow = windows[0];
        var spinWindow = windows[1];

        new Thread(() -> {
            int numFramesInFlight = 2;
            var commandPools = IntStream.range(0, numFramesInFlight).mapToLong(index -> boiler.commands.createPool(
                    VK_COMMAND_POOL_CREATE_TRANSIENT_BIT, boiler.queueFamilies().graphics().index(), "FillPool" + index
            )).toArray();
            var commandBuffers = IntStream.range(0, numFramesInFlight).mapToObj(index -> boiler.commands.createPrimaryBuffers(
                    commandPools[index], 1, "FillCommandBuffer" + index
            )[0]).toList();
            var fences = boiler.sync.fenceBank.borrowFences(numFramesInFlight, true, "FillCommandFence");

            long currentFrame = 0;
            while (!glfwWindowShouldClose(fillWindow.glfwWindow)) {
                var swapchainImage = fillWindow.acquireSwapchainImageWithSemaphore(VK_PRESENT_MODE_FIFO_KHR);
                if (swapchainImage == null) {
                    try {
                        sleep(100);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    continue;
                }

                WaitSemaphore[] waitSemaphores = {
                        new WaitSemaphore(swapchainImage.acquireSemaphore(), VK_PIPELINE_STAGE_TRANSFER_BIT)
                };

                try (var stack = stackPush()) {
                    int frameIndex = (int) (currentFrame % numFramesInFlight);
                    fences[frameIndex].waitAndReset();
                    assertVkSuccess(vkResetCommandPool(
                            boiler.vkDevice(), commandPools[frameIndex], 0
                    ), "ResetCommandPool", "FillCommand");

                    var commandBuffer = commandBuffers.get(frameIndex);
                    var recorder = CommandRecorder.begin(commandBuffer, boiler, stack, "Fill");

                    recorder.transitionColorLayout(
                            swapchainImage.vkImage(),
                            ResourceUsage.fromPresent(VK_PIPELINE_STAGE_TRANSFER_BIT),
                            ResourceUsage.TRANSFER_DEST
                    );

                    var clearColor = VkClearColorValue.calloc(stack);
                    clearColor.float32(0, 1f);
                    clearColor.float32(1, 0f);
                    clearColor.float32(2, 1f);
                    clearColor.float32(3, 1f);

                    vkCmdClearColorImage(
                            commandBuffer, swapchainImage.vkImage(), VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, clearColor,
                            boiler.images.subresourceRange(stack, null, VK_IMAGE_ASPECT_COLOR_BIT)
                    );

                    recorder.transitionColorLayout(swapchainImage.vkImage(), ResourceUsage.TRANSFER_DEST, ResourceUsage.PRESENT);

                    recorder.end();

                    var renderSubmission = boiler.queueFamilies().graphics().queues().get(0).submit(
                            commandBuffer, "Fill", waitSemaphores,
                            fences[frameIndex], swapchainImage.presentSemaphore()
                    );

                    fillWindow.presentSwapchainImage(swapchainImage, renderSubmission);
                }
                currentFrame += 1;
            }

            for (var fence : fences) {
                fence.waitIfSubmitted();
                boiler.sync.fenceBank.returnFence(fence);
            }
            for (var commandPool : commandPools) vkDestroyCommandPool(boiler.vkDevice(), commandPool, null);
            fillWindow.destroy();
        }).start();

        new Thread(() -> {
            long pipeline, pipelineLayout;
            try (var stack = stackPush()) {
                var pushConstants = VkPushConstantRange.calloc(1, stack);
                pushConstants.offset(0);
                pushConstants.size(8);
                pushConstants.stageFlags(VK_SHADER_STAGE_VERTEX_BIT);

                pipelineLayout = boiler.pipelines.createLayout(stack, pushConstants, "SpinLayout");

                var builder = new GraphicsPipelineBuilder(boiler, stack);

                builder.simpleShaderStages(
                        "SpinShader", "com/github/knokko/boiler/samples/graphics/spin.vert.spv",
                        "com/github/knokko/boiler/samples/graphics/spin.frag.spv"
                );
                builder.noVertexInput();
                builder.simpleInputAssembly();
                builder.dynamicViewports(1);
                builder.simpleRasterization(VK_CULL_MODE_NONE);
                builder.noMultisampling();
                builder.noDepthStencil();
                builder.noColorBlending(1);
                builder.dynamicStates(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR);
                builder.ciPipeline.layout(pipelineLayout);
                builder.dynamicRendering(0, VK_FORMAT_UNDEFINED, VK_FORMAT_UNDEFINED, spinWindow.surfaceFormat);

                pipeline = builder.build("SpinPipeline");
            }

            var commandPool = boiler.commands.createPool(0, boiler.queueFamilies().graphics().index(), "SpinPool");
            var commandBuffer = boiler.commands.createPrimaryBuffers(commandPool, 1, "SpinCommandBuffer")[0];

            var fence = boiler.sync.fenceBank.borrowFence(true, "SpinCommandFence");

            var associatedResources = new SwapchainResourceManager<Long>(swapchainImage -> {
                try (var stack = stackPush()) {
                    return boiler.images.createSimpleView(
                            stack, swapchainImage.vkImage(), spinWindow.surfaceFormat,
                            VK_IMAGE_ASPECT_COLOR_BIT, "SpinSwapchainImageView"
                    );
                }
            }, imageView -> vkDestroyImageView(boiler.vkDevice(), imageView, null));

            while (!glfwWindowShouldClose(spinWindow.glfwWindow)) {
                var swapchainImage = spinWindow.acquireSwapchainImageWithFence(VK_PRESENT_MODE_MAILBOX_KHR);
                if (swapchainImage == null) {
                    try {
                        sleep(100);
                        continue;
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }

                try (var stack = stackPush()) {
                    fence.waitAndReset();
                    swapchainImage.acquireFence().awaitSignal();

                    vkResetCommandPool(boiler.vkDevice(), commandPool, 0);

                    var recorder = CommandRecorder.begin(commandBuffer, boiler, stack, "SpinCommands");

                    recorder.transitionColorLayout(
                            swapchainImage.vkImage(),
                            ResourceUsage.fromPresent(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT),
                            ResourceUsage.COLOR_ATTACHMENT_WRITE
                    );

                    var colorAttachments = VkRenderingAttachmentInfo.calloc(1, stack);
                    recorder.simpleColorRenderingAttachment(
                            colorAttachments.get(0), associatedResources.get(swapchainImage), VK_ATTACHMENT_LOAD_OP_CLEAR,
                            VK_ATTACHMENT_STORE_OP_STORE, 0f, 0f, 0.7f, 1f
                    );

                    recorder.beginSimpleDynamicRendering(
                            swapchainImage.width(), swapchainImage.height(),
                            colorAttachments, null, null
                    );

                    recorder.dynamicViewportAndScissor(swapchainImage.width(), swapchainImage.height());
                    vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);

                    long periodFactor = 6_000_000L;
                    long period = 360L * periodFactor;
                    long progress = System.nanoTime() % period;
                    float angle = toRadians(progress / (float) periodFactor);
                    var pushConstants = stack.callocFloat(2);
                    pushConstants.put(0, 0.8f * cos(angle));
                    pushConstants.put(1, 0.8f * sin(angle));
                    vkCmdPushConstants(commandBuffer, pipelineLayout, VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstants);

                    vkCmdDraw(commandBuffer, 3, 1, 0, 0);

                    recorder.endDynamicRendering();

                    recorder.transitionColorLayout(
                            swapchainImage.vkImage(), ResourceUsage.COLOR_ATTACHMENT_WRITE, ResourceUsage.PRESENT
                    );

                    recorder.end();

                    var renderSubmission = boiler.queueFamilies().graphics().queues().get(0).submit(
                            commandBuffer, "SpinSubmission", null, fence, swapchainImage.presentSemaphore()
                    );

                    spinWindow.presentSwapchainImage(swapchainImage, renderSubmission);
                }
            }

            fence.waitIfSubmitted();
            boiler.sync.fenceBank.returnFence(fence);
            vkDestroyCommandPool(boiler.vkDevice(), commandPool, null);
            vkDestroyPipeline(boiler.vkDevice(), pipeline, null);
            vkDestroyPipelineLayout(boiler.vkDevice(), pipelineLayout, null);
            spinWindow.destroy();
        }).start();

        var windowLoop = new WindowLoop();
        windowLoop.addWindow(spinWindow);
        windowLoop.addWindow(fillWindow);

        //noinspection resource
        glfwSetMouseButtonCallback(spinWindow.glfwWindow, (clickedWindow, button, action, modifiers) -> {
            if (action == GLFW_PRESS) windowLoop.addWindow(createNewWindowThread(boiler));
        });

        windowLoop.runMain();

        boiler.destroyInitialObjects();
    }

    private static VkbWindow createNewWindowThread(BoilerInstance boiler) {
        var rng = new Random();
        float red = rng.nextFloat();
        float green = rng.nextFloat();
        float blue = rng.nextFloat();

        String contextSuffix = String.format("Extra(%.1f, %.1f, %.1f)", red, green, blue);
        var window = boiler.addWindow(new WindowBuilder(1000, 700, VK_IMAGE_USAGE_TRANSFER_DST_BIT).title(contextSuffix));

        var extraThread = new Thread(() -> {
            var commandPool = boiler.commands.createPool(
                    0, boiler.queueFamilies().graphics().index(), "CommandPool" + contextSuffix
            );
            var commandBuffer = boiler.commands.createPrimaryBuffers(commandPool, 1, "CommandBuffer " + contextSuffix)[0];
            var fence = boiler.sync.fenceBank.borrowFence(true, "Fence" + contextSuffix);

            while (!glfwWindowShouldClose(window.glfwWindow)) {
                var swapchainImage = window.acquireSwapchainImageWithFence(VK_PRESENT_MODE_FIFO_KHR);
                if (swapchainImage == null) {
                    try {
                        sleep(100);
                        continue;
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }

                try (var stack = stackPush()) {
                    fence.waitAndReset();
                    assertVkSuccess(vkResetCommandPool(
                            boiler.vkDevice(), commandPool, 0
                    ), "ResetCommandPool", "Reset" + contextSuffix);

                    var recorder = CommandRecorder.begin(commandBuffer, boiler, stack, "Record" + contextSuffix);
                    recorder.transitionColorLayout(
                            swapchainImage.vkImage(),
                            ResourceUsage.fromPresent(VK_PIPELINE_STAGE_TRANSFER_BIT),
                            ResourceUsage.TRANSFER_DEST
                    );

                    var clearColor = VkClearColorValue.calloc(stack);
                    clearColor.float32(0, red);
                    clearColor.float32(1, green);
                    clearColor.float32(2, blue);
                    clearColor.float32(3, 1f);

                    // TODO Add helper method for this
                    vkCmdClearColorImage(
                            commandBuffer, swapchainImage.vkImage(), VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                            clearColor, boiler.images.subresourceRange(stack, null, VK_IMAGE_ASPECT_COLOR_BIT)
                    );

                    recorder.transitionColorLayout(swapchainImage.vkImage(), ResourceUsage.TRANSFER_DEST, ResourceUsage.PRESENT);
                    recorder.end();

                    swapchainImage.acquireFence().awaitSignal();
                    var renderSubmission = boiler.queueFamilies().graphics().queues().get(0).submit(
                            commandBuffer, contextSuffix, null, fence, swapchainImage.presentSemaphore()
                    );

                    window.presentSwapchainImage(swapchainImage, renderSubmission);
                }
            }

            fence.waitIfSubmitted();
            boiler.sync.fenceBank.returnFence(fence);
            vkDestroyCommandPool(boiler.vkDevice(), commandPool, null);
            window.destroy();
        });
        extraThread.start();
        return window;
    }
}
