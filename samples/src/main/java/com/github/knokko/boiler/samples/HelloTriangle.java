package com.github.knokko.boiler.samples;

import com.github.knokko.boiler.builder.BoilerBuilder;
import com.github.knokko.boiler.builder.WindowBuilder;
import com.github.knokko.boiler.commands.CommandRecorder;
import com.github.knokko.boiler.pipelines.GraphicsPipelineBuilder;
import com.github.knokko.boiler.pipelines.ShaderInfo;
import com.github.knokko.boiler.window.SwapchainResourceManager;
import com.github.knokko.boiler.sync.VkbFence;
import com.github.knokko.boiler.sync.WaitSemaphore;
import org.lwjgl.vulkan.*;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static java.lang.Thread.sleep;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwWindowShouldClose;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memFloatBuffer;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_FIFO_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.VK10.*;

public class HelloTriangle {

    public static void main(String[] args) throws InterruptedException {
        var boiler = new BoilerBuilder(
                VK_API_VERSION_1_0, "HelloTriangle", VK_MAKE_VERSION(0, 1, 0)
        )
                .validation().forbidValidationErrors()
                .addWindow(new WindowBuilder(1000, 800, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT))
                .build();

        int numFramesInFlight = 3;
        var commandPool = boiler.commands.createPool(
                VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT | VK_COMMAND_POOL_CREATE_TRANSIENT_BIT,
                boiler.queueFamilies().graphics().index(), "Drawing"
        );
        var commandBuffers = boiler.commands.createPrimaryBuffers(commandPool, numFramesInFlight, "Drawing");
        var commandFences = boiler.sync.fenceBank.borrowFences(numFramesInFlight, true, "CommandFence");
        long graphicsPipeline;
        long pipelineLayout;
        long renderPass;

        try (var stack = stackPush()) {
            pipelineLayout = boiler.pipelines.createLayout(stack, null, "DrawingLayout");

            var attachments = VkAttachmentDescription.calloc(1, stack);
            var colorAttachment = attachments.get(0);
            colorAttachment.format(boiler.window().surfaceFormat);
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
            ), "CreateRenderPass", "TrianglePass");
            renderPass = pRenderPass.get(0);
        }

        try (var stack = stackPush()) {
            var vertexModule = boiler.pipelines.createShaderModule(
                    stack, "com/github/knokko/boiler/samples/graphics/triangle.vert.spv", "TriangleVertices"
            );
            var fragmentModule = boiler.pipelines.createShaderModule(
                    stack, "com/github/knokko/boiler/samples/graphics/triangle.frag.spv", "TriangleFragments"
            );

            var vertexBindings = VkVertexInputBindingDescription.calloc(1, stack);
            vertexBindings.binding(0);
            vertexBindings.stride(4 * (2 + 3));
            vertexBindings.inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

            var vertexAttributes = VkVertexInputAttributeDescription.calloc(2, stack);
            var attributePosition = vertexAttributes.get(0);
            attributePosition.location(0);
            attributePosition.binding(0);
            attributePosition.format(VK_FORMAT_R32G32_SFLOAT);
            attributePosition.offset(0);
            var attributeColor = vertexAttributes.get(1);
            attributeColor.location(1);
            attributeColor.binding(0);
            attributeColor.format(VK_FORMAT_R32G32B32_SFLOAT);
            attributeColor.offset(4 * 2);

            var ciVertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack);
            ciVertexInput.sType$Default();
            ciVertexInput.pVertexBindingDescriptions(vertexBindings);
            ciVertexInput.pVertexAttributeDescriptions(vertexAttributes);

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

            graphicsPipeline = pipelineBuilder.build("TrianglePipeline");

            vkDestroyShaderModule(boiler.vkDevice(), vertexModule, null);
            vkDestroyShaderModule(boiler.vkDevice(), fragmentModule, null);
        }

        var vertexBuffer = boiler.buffers.createMapped(
                3 * 4 * (2 + 3), VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, "TriangleVertices"
        );
        var vertices = memFloatBuffer(vertexBuffer.hostAddress(), 3 * (2 + 3));
        // Put color (1, 0, 0) at position (-1, 1)
        vertices.put(-1f);
        vertices.put(1f);
        vertices.put(1f);
        vertices.put(0f);
        vertices.put(0f);
        // Put color (0, 0, 1) at position (1, 1)
        vertices.put(1f);
        vertices.put(1f);
        vertices.put(0f);
        vertices.put(0f);
        vertices.put(1f);
        // Put color (0, 1, 0) at position (0, -1)
        vertices.put(0f);
        vertices.put(-1f);
        vertices.put(0f);
        vertices.put(1f);
        vertices.put(0f);

        long frameCounter = 0;
        var swapchainResources = new SwapchainResourceManager<>(swapchainImage -> {
            try (var stack = stackPush()) {
                long imageView = boiler.images.createSimpleView(
                        stack, swapchainImage.vkImage(), boiler.window().surfaceFormat,
                        VK_IMAGE_ASPECT_COLOR_BIT, "SwapchainView " + swapchainImage.index()
                );

                long framebuffer = boiler.images.createFramebuffer(
                        stack, renderPass, swapchainImage.width(), swapchainImage.height(),
                        "TriangleFramebuffer", imageView
                );

                return new AssociatedSwapchainResources(framebuffer, imageView);
            }
        }, resources -> {
            vkDestroyFramebuffer(boiler.vkDevice(), resources.framebuffer, null);
            vkDestroyImageView(boiler.vkDevice(), resources.imageView, null);
        });

        long referenceTime = System.currentTimeMillis();
        long referenceFrames = 0;

        while (!glfwWindowShouldClose(boiler.window().glfwWindow)) {
            glfwPollEvents();

            long currentTime = System.currentTimeMillis();
            if (currentTime > 1000 + referenceTime) {
                System.out.println("FPS is " + (frameCounter - referenceFrames));
                referenceTime = currentTime;
                referenceFrames = frameCounter;
            }

            try (var stack = stackPush()) {
                var swapchainImage = boiler.window().acquireSwapchainImageWithSemaphore(VK_PRESENT_MODE_FIFO_KHR);
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
                VkbFence fence = commandFences[frameIndex];
                fence.waitAndReset();

                var recorder = CommandRecorder.begin(
                        commandBuffer, boiler, stack,
                        VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT,
                        "DrawCommands"
                );

                var pColorClear = VkClearValue.calloc(1, stack);
                pColorClear.color().float32(stack.floats(0.2f, 0.2f, 0.2f, 1f));

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

                vkCmdBindVertexBuffers(commandBuffer, 0, stack.longs(vertexBuffer.vkBuffer()), stack.longs(0));

                vkCmdDraw(commandBuffer, 3, 1, 0, 0);
                vkCmdEndRenderPass(commandBuffer);
                assertVkSuccess(vkEndCommandBuffer(commandBuffer), "TriangleDrawing", null);

                var renderSubmission = boiler.queueFamilies().graphics().queues().get(0).submit(
                        commandBuffer, "SubmitDraw", waitSemaphores, fence, swapchainImage.presentSemaphore()
                );

                boiler.window().presentSwapchainImage(swapchainImage, renderSubmission);
                frameCounter += 1;
            }
        }

        boiler.sync.fenceBank.awaitSubmittedFences();
        boiler.sync.fenceBank.returnFences(commandFences);

        vkDestroyPipelineLayout(boiler.vkDevice(), pipelineLayout, null);
        vkDestroyPipeline(boiler.vkDevice(), graphicsPipeline, null);
        vkDestroyRenderPass(boiler.vkDevice(), renderPass, null);
        vkDestroyCommandPool(boiler.vkDevice(), commandPool, null);
        vertexBuffer.destroy(boiler.vmaAllocator());
        boiler.destroyInitialObjects();
    }

    private record AssociatedSwapchainResources(
            long framebuffer,
            long imageView
    ) {}
}

