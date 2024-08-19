package com.github.knokko.boiler.samples;

import com.github.knokko.boiler.builder.BoilerBuilder;
import com.github.knokko.boiler.builder.WindowBuilder;
import com.github.knokko.boiler.builder.device.SimpleDeviceSelector;
import com.github.knokko.boiler.builder.instance.ValidationFeatures;
import com.github.knokko.boiler.builder.window.SimpleCompositeAlphaPicker;
import com.github.knokko.boiler.commands.CommandRecorder;
import com.github.knokko.boiler.sync.ResourceUsage;
import com.github.knokko.boiler.sync.WaitSemaphore;
import com.github.knokko.boiler.window.WindowLoop;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.VkClearColorValue;
import static com.github.knokko.boiler.util.ReflectionHelper.getIntConstantName;
import static java.lang.Math.*;
import static java.lang.Thread.sleep;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memUTF8;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.VK10.*;

public class TranslucentWindowPlayground {

    public static void main(String[] args) throws InterruptedException {
        //noinspection resource
        glfwSetErrorCallback(
                (error, description) -> System.out.println("[GLFW]: " + memUTF8(description) + " (" + error + ")")
        );

        // Wayland's windows have cool transparency support
        if (glfwPlatformSupported(GLFW_PLATFORM_WAYLAND)) glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_WAYLAND);
        if (!glfwInit()) {
            // The fact that glfwPlatFormSupported(GLFW_PLATFORM_WAYLAND) returned `true` does not necessarily mean
            // that this machine actually supports Wayland. If Wayland is not supported by this machine, glfwInit()
            // will fail (return false), and we retry with X11.
            if (glfwPlatformSupported(GLFW_PLATFORM_WAYLAND) && glfwPlatformSupported(GLFW_PLATFORM_X11)) {
                glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_X11);
                System.out.println("This machine doesn't seem to support Wayland; falling back to X11...");
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
                .addWindow(new WindowBuilder(
                        800, 600, VK_IMAGE_USAGE_TRANSFER_DST_BIT
                ).compositeAlphaPicker(new SimpleCompositeAlphaPicker(
                        VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR,
                        VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR,
                        VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR,
                        VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR
                )))
                // Avoid annoying crashes on laptops with multiple GPUs by preferring the integrated GPU
                .physicalDeviceSelector(new SimpleDeviceSelector(VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU))
                .build();

        System.out.printf(
                "GLFW platform is %s and GLFW transparent framebuffer is %b and composite alpha mode is %s\n",
                getIntConstantName(GLFW.class, glfwGetPlatform(), "GLFW_PLATFORM", "", "unknown"),
                glfwGetWindowAttrib(boiler.window().glfwWindow, GLFW_TRANSPARENT_FRAMEBUFFER),
                getIntConstantName(KHRSurface.class, boiler.window().swapchainCompositeAlpha,
                        "VK_COMPOSITE_ALPHA", "BIT_KHR", "unknown")
        );

        var renderThread = new Thread(() -> {
            var commandPool = boiler.commands.createPool(
                    VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT,
                    boiler.queueFamilies().graphics().index(), "Fill"
            );
            var commandBuffers = boiler.commands.createPrimaryBuffers(commandPool, 5, "Fill");

            var commandFences = boiler.sync.fenceBank.borrowFences(commandBuffers.length, true, "Acquire");

            int counter = 0;

            while (!glfwWindowShouldClose(boiler.window().glfwWindow)) {
                try (var stack = stackPush()) {
                    int commandIndex = counter % commandFences.length;

                    var acquired = boiler.window().acquireSwapchainImageWithSemaphore(VK_PRESENT_MODE_MAILBOX_KHR);
                    if (acquired == null) {
                        //noinspection BusyWait
                        sleep(100);
                        continue;
                    }

                    var fence = commandFences[commandIndex];
                    fence.waitAndReset();

                    var commandBuffer = commandBuffers[commandIndex];
                    var recorder = CommandRecorder.begin(
                            commandBuffer, boiler, stack,
                            VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT,
                            "Fill"
                    );

                    recorder.transitionColorLayout(
                            acquired.vkImage(),
                            ResourceUsage.fromPresent(VK_PIPELINE_STAGE_TRANSFER_BIT),
                            ResourceUsage.TRANSFER_DEST
                    );

                    float alpha = 0.1f + 0.9f * (float) (abs(sin(System.currentTimeMillis() / 250.0)));
                    float colorScale = boiler.window().swapchainCompositeAlpha == VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR ? 1f : alpha;
                    var pClearColor = VkClearColorValue.calloc(stack);
                    pClearColor.float32(stack.floats(0f, 0.6f * colorScale, colorScale, alpha));

                    var pRanges = boiler.images.subresourceRange(stack, null, VK_IMAGE_ASPECT_COLOR_BIT);

                    vkCmdClearColorImage(commandBuffer, acquired.vkImage(), VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, pClearColor, pRanges);

                    recorder.transitionColorLayout(acquired.vkImage(), ResourceUsage.TRANSFER_DEST, ResourceUsage.PRESENT);
                    recorder.end();

                    var renderSubmission = boiler.queueFamilies().graphics().queues().get(0).submit(
                            commandBuffer, "Fill", new WaitSemaphore[] { new WaitSemaphore(
                                    acquired.acquireSemaphore(), VK_PIPELINE_STAGE_TRANSFER_BIT
                            ) }, fence, acquired.presentSemaphore()
                    );

                    boiler.window().presentSwapchainImage(acquired, renderSubmission);
                } catch (InterruptedException e) {
					throw new RuntimeException(e);
				}

				counter += 1;
            }

            boiler.sync.fenceBank.awaitSubmittedFences();
            boiler.sync.fenceBank.returnFences(commandFences);
            vkDestroyCommandPool(boiler.vkDevice(), commandPool, null);

            boiler.window().destroy();
        });

        renderThread.start();

        var windowLoop = new WindowLoop();
        windowLoop.addWindow(boiler.window());

        windowLoop.runMain();

        boiler.destroyInitialObjects();
    }
}
