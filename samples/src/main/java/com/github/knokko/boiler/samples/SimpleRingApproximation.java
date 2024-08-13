package com.github.knokko.boiler.samples;

import com.github.knokko.boiler.builder.BoilerBuilder;
import com.github.knokko.boiler.builder.BoilerSwapchainBuilder;
import com.github.knokko.boiler.commands.CommandRecorder;
import com.github.knokko.boiler.pipelines.GraphicsPipelineBuilder;
import com.github.knokko.boiler.window.SwapchainResourceManager;
import com.github.knokko.boiler.sync.ResourceUsage;
import com.github.knokko.boiler.sync.WaitSemaphore;
import org.lwjgl.vulkan.*;

import static java.lang.Thread.sleep;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwWindowShouldClose;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.VK_API_VERSION_1_1;

public class SimpleRingApproximation {

    public static void main(String[] args) throws InterruptedException {
        var boiler = new BoilerBuilder(
                VK_API_VERSION_1_1, "SimpleRingApproximation", VK_MAKE_VERSION(0, 2, 0)
        )
                .validation()
                .enableDynamicRendering()
                .window(0L, 1000, 800, new BoilerSwapchainBuilder(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT))
                .build();

        int numFramesInFlight = 3;
        var commandPool = boiler.commands.createPool(
                VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT,
                boiler.queueFamilies().graphics().index(),
                "Drawing"
        );
        var commandBuffers = boiler.commands.createPrimaryBuffers(commandPool, numFramesInFlight, "Drawing");
        var commandFences = boiler.sync.fenceBank.borrowFences(numFramesInFlight, true, "Fence");
        long graphicsPipeline;
        long pipelineLayout;

        try (var stack = stackPush()) {
            var pushConstants = VkPushConstantRange.calloc(1, stack);
            pushConstants.stageFlags(VK_SHADER_STAGE_VERTEX_BIT);
            pushConstants.offset(0);
            pushConstants.size(20);

            pipelineLayout = boiler.pipelines.createLayout(stack, pushConstants, "DrawingLayout");
        }

        try (var stack = stackPush()) {
            var pipelineBuilder = new GraphicsPipelineBuilder(boiler, stack);
            pipelineBuilder.simpleShaderStages(
                    "Ring", "com/github/knokko/boiler/samples/graphics/ring.vert.spv",
                    "com/github/knokko/boiler/samples/graphics/ring.frag.spv"
            );
            pipelineBuilder.noVertexInput();
            pipelineBuilder.simpleInputAssembly();
            pipelineBuilder.dynamicViewports(1);
            pipelineBuilder.simpleRasterization(VK_CULL_MODE_NONE);
            pipelineBuilder.noMultisampling();
            pipelineBuilder.noDepthStencil();
            pipelineBuilder.noColorBlending(1);
            pipelineBuilder.dynamicStates(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR);
            pipelineBuilder.dynamicRendering(
                    0, VK_FORMAT_UNDEFINED, VK_FORMAT_UNDEFINED,
                    boiler.swapchainSettings.surfaceFormat().format()
            );
            pipelineBuilder.ciPipeline.layout(pipelineLayout);

            graphicsPipeline = pipelineBuilder.build("RingApproximation");
        }

        long frameCounter = 0;
        var swapchainResources = new SwapchainResourceManager<>(swapchainImage -> {
            try (var stack = stackPush()) {
                long imageView = boiler.images.createSimpleView(
                        stack, swapchainImage.vkImage(), boiler.swapchainSettings.surfaceFormat().format(),
                        VK_IMAGE_ASPECT_COLOR_BIT, "SwapchainView" + swapchainImage.imageIndex()
                );

                return new AssociatedSwapchainResources(imageView);
            }
        }, resources -> {
            vkDestroyImageView(boiler.vkDevice(), resources.imageView, null);
        });

        long referenceTime = System.currentTimeMillis();
        long referenceFrames = 0;

        while (!glfwWindowShouldClose(boiler.glfwWindow())) {
            glfwPollEvents();

            long currentTime = System.currentTimeMillis();
            if (currentTime > 1000 + referenceTime) {
                System.out.println("FPS is " + (frameCounter - referenceFrames));
                referenceTime = currentTime;
                referenceFrames = frameCounter;
            }

            try (var stack = stackPush()) {
                var swapchainImage = boiler.swapchains.acquireNextImage(VK_PRESENT_MODE_MAILBOX_KHR);
                if (swapchainImage == null) {
                    //noinspection BusyWait
                    sleep(100);
                    continue;
                }

                var imageResources = swapchainResources.get(swapchainImage);
                WaitSemaphore[] waitSemaphores = { new WaitSemaphore(
                        swapchainImage.acquireSemaphore(), VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                )};

                int frameIndex = (int) (frameCounter % numFramesInFlight);
                var commandBuffer = commandBuffers[frameIndex];
                var fence = commandFences[frameIndex];
                fence.waitAndReset(stack);

                var recorder = CommandRecorder.begin(commandBuffer, boiler, stack, "RingApproximation");

                recorder.transitionColorLayout(
                        swapchainImage.vkImage(),
                        ResourceUsage.fromPresent(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT),
                        ResourceUsage.COLOR_ATTACHMENT_WRITE
                );

                var colorAttachments = VkRenderingAttachmentInfo.calloc(1, stack);
                recorder.simpleColorRenderingAttachment(
                        colorAttachments.get(0), imageResources.imageView(),
                        VK_ATTACHMENT_LOAD_OP_CLEAR, VK_ATTACHMENT_STORE_OP_STORE,
                        0.07f, 0.4f, 0.6f, 1f
                );

                recorder.beginSimpleDynamicRendering(
                        swapchainImage.width(), swapchainImage.height(),
                        colorAttachments, null, null
                );

                recorder.dynamicViewportAndScissor(swapchainImage.width(), swapchainImage.height());
                vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicsPipeline);

                int numTriangles = 30_000_000;
                var pushConstants = stack.calloc(20);
                pushConstants.putFloat(0, 0.6f);
                pushConstants.putFloat(4, 0.8f);
                pushConstants.putFloat(8, 0.2f);
                pushConstants.putFloat(12, -0.1f);
                pushConstants.putInt(16, 2 * numTriangles);

                vkCmdPushConstants(commandBuffer, pipelineLayout, VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstants);

                vkCmdDraw(commandBuffer, 6 * numTriangles, 1, 0, 0);
                recorder.endDynamicRendering();

                recorder.transitionColorLayout(
                        swapchainImage.vkImage(), ResourceUsage.COLOR_ATTACHMENT_WRITE, ResourceUsage.PRESENT
                );

                recorder.end();

                boiler.queueFamilies().graphics().queues().get(0).submit(
                        commandBuffer, "RingApproximation", waitSemaphores, fence, swapchainImage.presentSemaphore()
                );

                // Note that we could just use boiler.swapchains.presentImage(swapchainImage, fence),
                // but this is a nice way to test a different overload of BoilerSwapchains.presentImage
                boiler.swapchains.presentImage(swapchainImage, fence::isSignaled);
                frameCounter += 1;
            }
        }

        boiler.sync.fenceBank.awaitSubmittedFences();
        boiler.sync.fenceBank.returnFences(commandFences);

        vkDestroyPipelineLayout(boiler.vkDevice(), pipelineLayout, null);
        vkDestroyPipeline(boiler.vkDevice(), graphicsPipeline, null);
        vkDestroyCommandPool(boiler.vkDevice(), commandPool, null);
        boiler.destroyInitialObjects();
    }

    private record AssociatedSwapchainResources(long imageView) {}
}
