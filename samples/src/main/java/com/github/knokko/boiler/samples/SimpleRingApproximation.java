package com.github.knokko.boiler.samples;

import com.github.knokko.boiler.builder.BoilerBuilder;
import com.github.knokko.boiler.builder.BoilerSwapchainBuilder;
import com.github.knokko.boiler.commands.CommandRecorder;
import com.github.knokko.boiler.pipelines.GraphicsPipelineBuilder;
import com.github.knokko.boiler.pipelines.ShaderInfo;
import com.github.knokko.boiler.swapchain.SwapchainResourceManager;
import com.github.knokko.boiler.sync.WaitSemaphore;
import org.lwjgl.vulkan.*;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static java.lang.Thread.sleep;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwWindowShouldClose;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.VK_API_VERSION_1_1;

public class SimpleRingApproximation {

    public static void main(String[] args) throws InterruptedException {
        var boiler = new BoilerBuilder(
                VK_API_VERSION_1_1, "SimpleRingApproximation", VK_MAKE_VERSION(0, 2, 0)
        )
                .validation()
                .window(0L, 1000, 800, new BoilerSwapchainBuilder(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT))
                .build();

        int numFramesInFlight = 3;
        var commandPool = boiler.commands.createPool(
                VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT,
                boiler.queueFamilies().graphics().index(),
                "Drawing"
        );
        var commandBuffers = boiler.commands.createPrimaryBuffers(commandPool, numFramesInFlight, "Drawing");
        long[] commandFences = boiler.sync.createFences(true, numFramesInFlight, "Fence");
        long graphicsPipeline;
        long pipelineLayout;
        long renderPass;

        try (var stack = stackPush()) {
            var pushConstants = VkPushConstantRange.calloc(1, stack);
            pushConstants.stageFlags(VK_SHADER_STAGE_VERTEX_BIT);
            pushConstants.offset(0);
            pushConstants.size(20);

            pipelineLayout = boiler.pipelines.createLayout(stack, pushConstants, "DrawingLayout");

            var attachments = VkAttachmentDescription.calloc(1, stack);
            var colorAttachment = attachments.get(0);
            colorAttachment.format(boiler.swapchainSettings.surfaceFormat().format());
            colorAttachment.samples(VK_SAMPLE_COUNT_1_BIT);
            colorAttachment.loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR);
            colorAttachment.storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            colorAttachment.stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE);
            colorAttachment.stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE);
            colorAttachment.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            colorAttachment.finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);

            var colorReference = VkAttachmentReference.calloc(1, stack);
            colorReference.attachment(0);
            colorReference.layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

            var subpass = VkSubpassDescription.calloc(1, stack);
            subpass.pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS);
            subpass.pInputAttachments(null);
            subpass.colorAttachmentCount(1);
            subpass.pColorAttachments(colorReference);
            subpass.pResolveAttachments(null);
            subpass.pDepthStencilAttachment(null);
            subpass.pPreserveAttachments(null);

            var dependency = VkSubpassDependency.calloc(1, stack);
            dependency.srcSubpass(VK_SUBPASS_EXTERNAL);
            dependency.dstSubpass(0);
            dependency.srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
            dependency.srcAccessMask(0);
            dependency.dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
            dependency.dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);

            var ciRenderPass = VkRenderPassCreateInfo.calloc(stack);
            ciRenderPass.sType$Default();
            ciRenderPass.pAttachments(attachments);
            ciRenderPass.pSubpasses(subpass);
            ciRenderPass.pDependencies(dependency);

            var pRenderPass = stack.callocLong(1);
            assertVkSuccess(vkCreateRenderPass(
                    boiler.vkDevice(), ciRenderPass, null, pRenderPass
            ), "CreateRenderPass", "RingApproximation");
            renderPass = pRenderPass.get(0);
        }

        try (var stack = stackPush()) {
            var vertexModule = boiler.pipelines.createShaderModule(
                    stack, "com/github/knokko/boiler/samples/graphics/ring.vert.spv", "RingVertices"
            );
            var fragmentModule = boiler.pipelines.createShaderModule(
                    stack, "com/github/knokko/boiler/samples/graphics/ring.frag.spv", "RingFragments"
            );

            var ciVertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack);
            ciVertexInput.sType$Default();
            ciVertexInput.pVertexBindingDescriptions(null);
            ciVertexInput.pVertexAttributeDescriptions(null);

            var pipelineBuilder = new GraphicsPipelineBuilder(boiler, stack);
            pipelineBuilder.shaderStages(
                    new ShaderInfo(VK_SHADER_STAGE_VERTEX_BIT, vertexModule, null),
                    new ShaderInfo(VK_SHADER_STAGE_FRAGMENT_BIT, fragmentModule, null)
            );
            pipelineBuilder.ciPipeline.pVertexInputState(ciVertexInput);
            pipelineBuilder.simpleInputAssembly();
            pipelineBuilder.dynamicViewports(1);
            pipelineBuilder.simpleRasterization(VK_CULL_MODE_NONE);
            pipelineBuilder.noMultisampling();
            pipelineBuilder.noDepthStencil();
            pipelineBuilder.noColorBlending(1);
            pipelineBuilder.dynamicStates(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR);

            pipelineBuilder.ciPipeline.renderPass(renderPass);
            pipelineBuilder.ciPipeline.layout(pipelineLayout);

            graphicsPipeline = pipelineBuilder.build("RingApproximation");

            vkDestroyShaderModule(boiler.vkDevice(), vertexModule, null);
            vkDestroyShaderModule(boiler.vkDevice(), fragmentModule, null);
        }

        long frameCounter = 0;
        var swapchainResources = new SwapchainResourceManager<>(swapchainImage -> {
            try (var stack = stackPush()) {
                long imageView = boiler.images.createSimpleView(
                        stack, swapchainImage.vkImage(), boiler.swapchainSettings.surfaceFormat().format(),
                        VK_IMAGE_ASPECT_COLOR_BIT, "SwapchainView" + swapchainImage.imageIndex()
                );

                long framebuffer = boiler.images.createFramebuffer(
                        stack, renderPass, swapchainImage.width(), swapchainImage.height(),
                        "RingFramebuffer", imageView
                );

                return new AssociatedSwapchainResources(framebuffer, imageView);
            }
        }, resources -> {
            vkDestroyFramebuffer(boiler.vkDevice(), resources.framebuffer, null);
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
                long fence = commandFences[frameIndex];
                boiler.sync.waitAndReset(stack, fence);

                var recorder = CommandRecorder.begin(commandBuffer, boiler, stack, "RingApproximation");

                var pColorClear = VkClearValue.calloc(1, stack);
                pColorClear.color().float32(stack.floats(0.07f, 0.4f, 0.6f, 1f));

                var biRenderPass = VkRenderPassBeginInfo.calloc(stack);
                biRenderPass.sType$Default();
                biRenderPass.renderPass(renderPass);
                biRenderPass.framebuffer(imageResources.framebuffer);
                biRenderPass.renderArea().offset().set(0, 0);
                biRenderPass.renderArea().extent().set(swapchainImage.width(), swapchainImage.height());
                biRenderPass.clearValueCount(1);
                biRenderPass.pClearValues(pColorClear);

                vkCmdBeginRenderPass(commandBuffer, biRenderPass, VK_SUBPASS_CONTENTS_INLINE);
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
                vkCmdEndRenderPass(commandBuffer);
                assertVkSuccess(vkEndCommandBuffer(commandBuffer), "RingApproximation", null);

                boiler.queueFamilies().graphics().queues().get(0).submit(
                        commandBuffer, "RingApproximation", waitSemaphores, fence, swapchainImage.presentSemaphore()
                );

                // Note that we could just use boiler.swapchains.presentImage(swapchainImage, fence),
                // but this is a nice way to test a different overload of BoilerSwapchains.presentImage
                boiler.swapchains.presentImage(swapchainImage, () ->
                    vkGetFenceStatus(boiler.vkDevice(), fence) == VK_SUCCESS
                );
                frameCounter += 1;
            }
        }

        vkDeviceWaitIdle(boiler.vkDevice());
        for (long fence : commandFences) vkDestroyFence(boiler.vkDevice(), fence, null);

        vkDestroyPipelineLayout(boiler.vkDevice(), pipelineLayout, null);
        vkDestroyPipeline(boiler.vkDevice(), graphicsPipeline, null);
        vkDestroyRenderPass(boiler.vkDevice(), renderPass, null);
        vkDestroyCommandPool(boiler.vkDevice(), commandPool, null);
        boiler.destroyInitialObjects();
    }

    private record AssociatedSwapchainResources(
            long framebuffer,
            long imageView
    ) {}
}
