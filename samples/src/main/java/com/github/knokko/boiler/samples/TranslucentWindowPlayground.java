package com.github.knokko.boiler.samples;

import com.github.knokko.boiler.builder.BoilerBuilder;
import com.github.knokko.boiler.builder.BoilerSwapchainBuilder;
import com.github.knokko.boiler.builder.instance.ValidationFeatures;
import com.github.knokko.boiler.builder.swapchain.SimpleCompositeAlphaPicker;
import com.github.knokko.boiler.commands.CommandRecorder;
import com.github.knokko.boiler.sync.ResourceUsage;
import com.github.knokko.boiler.sync.WaitSemaphore;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.VkClearColorValue;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static com.github.knokko.boiler.util.ReflectionHelper.getIntConstantName;
import static java.lang.Math.abs;
import static java.lang.Math.sin;
import static java.lang.Thread.sleep;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.VK10.*;

public class TranslucentWindowPlayground {

    public static void main(String[] args) throws InterruptedException {
        // Wayland's windows have cool transparency support
        if (glfwPlatformSupported(GLFW_PLATFORM_WAYLAND)) glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_WAYLAND);
        if (!glfwInit()) {
            // Sometimes, GLFW advertises Wayland support, although it doesn't really work...
            // In such cases, retry with X11 instead
            if (glfwPlatformSupported(GLFW_PLATFORM_WAYLAND) && glfwPlatformSupported(GLFW_PLATFORM_X11)) {
                glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_X11);
                System.out.println("GLFW appears to have Wayland issues; falling back to X11...");
            }
            if (!glfwInit()) throw new RuntimeException("Failed to init GLFW");
        }
        glfwWindowHint(GLFW_TRANSPARENT_FRAMEBUFFER, GLFW_TRUE);

        var boiler = new BoilerBuilder(
                VK_API_VERSION_1_0, "TranslucentWindowPlayground", VK_MAKE_VERSION(1, 0, 0)
        )
                .validation(new ValidationFeatures(
                        false, false, false, true, true
                ))
                .dontInitGLFW()
                .window(0, 800, 600, new BoilerSwapchainBuilder(
                        VK_IMAGE_USAGE_TRANSFER_DST_BIT
                ).compositeAlphaPicker(new SimpleCompositeAlphaPicker(
                        VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR,
                        VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR,
                        VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR,
                        VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR
                )))
                .build();

        System.out.printf(
                "GLFW platform is %s and GLFW transparent framebuffer is %b and composite alpha mode is %s\n",
                getIntConstantName(GLFW.class, glfwGetPlatform(), "GLFW_PLATFORM", "", "unknown"),
                glfwGetWindowAttrib(boiler.glfwWindow(), GLFW_TRANSPARENT_FRAMEBUFFER),
                getIntConstantName(KHRSurface.class, boiler.swapchainSettings.compositeAlpha(), "VK_COMPOSITE_ALPHA", "BIT_KHR", "unknown")
        );

        var commandPool = boiler.commands.createPool(
                VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT,
                boiler.queueFamilies().graphics().index(), "Fill"
        );
        var commandBuffers = boiler.commands.createPrimaryBuffers(commandPool, 5, "Fill");

        long[] commandFences = boiler.sync.createFences(true, commandBuffers.length, "Acquire");

        long oldSwapchainID = -1;

        int counter = 0;
        while(!glfwWindowShouldClose(boiler.glfwWindow())) {
            glfwPollEvents();
            try (var stack = stackPush()) {
                int commandIndex = counter % commandFences.length;

                var acquired = boiler.swapchains.acquireNextImage(VK_PRESENT_MODE_FIFO_KHR);
                if (acquired == null) {
                    //noinspection BusyWait
                    sleep(100);
                    continue;
                }

                if (acquired.swapchainID() != oldSwapchainID) {
                    oldSwapchainID = acquired.swapchainID();
                }

                long fence = commandFences[commandIndex];
                assertVkSuccess(vkWaitForFences(
                        boiler.vkDevice(), stack.longs(fence), true, 100_000_000
                ), "WaitForFences", "Acquire" + counter);
                assertVkSuccess(vkResetFences(boiler.vkDevice(), stack.longs(fence)), "ResetFences", "Acquire" + counter);

                var commandBuffer = commandBuffers[commandIndex];
                var recorder = CommandRecorder.begin(commandBuffer, boiler, stack, "Fill");

                recorder.transitionColorLayout(
                        acquired.vkImage(), VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        new ResourceUsage(0, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT),
                        new ResourceUsage(VK_ACCESS_TRANSFER_WRITE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT)
                );

                float alpha = 0.1f + 0.9f * (float) (abs(sin(System.currentTimeMillis() / 1000.0)));
                float colorScale = boiler.swapchainSettings.compositeAlpha() == VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR ? 1f : alpha;
                var pClearColor = VkClearColorValue.calloc(stack);
                pClearColor.float32(stack.floats(0f, 0.6f * colorScale, colorScale, alpha));

                var pRanges = boiler.images.subresourceRange(stack, null, VK_IMAGE_ASPECT_COLOR_BIT);

                vkCmdClearColorImage(commandBuffer, acquired.vkImage(), VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, pClearColor, pRanges);

                recorder.transitionColorLayout(
                        acquired.vkImage(), VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                        new ResourceUsage(VK_ACCESS_TRANSFER_WRITE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT),
                        new ResourceUsage(0, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT)
                );

                assertVkSuccess(vkEndCommandBuffer(commandBuffer), "EndCommandBuffer", "Fill");
                boiler.queueFamilies().graphics().queues().get(0).submit(
                        commandBuffer, "Fill", new WaitSemaphore[] { new WaitSemaphore(
                                acquired.acquireSemaphore(), VK_PIPELINE_STAGE_TRANSFER_BIT
                        ) }, fence, acquired.presentSemaphore()
                );

                boiler.swapchains.presentImage(acquired);
            }

            counter += 1;
        }

        assertVkSuccess(vkDeviceWaitIdle(boiler.vkDevice()), "DeviceWaitIdle", null);

        vkDestroyCommandPool(boiler.vkDevice(), commandPool, null);
        for (long fence : commandFences) vkDestroyFence(boiler.vkDevice(), fence, null);

        boiler.destroyInitialObjects();
    }
}
