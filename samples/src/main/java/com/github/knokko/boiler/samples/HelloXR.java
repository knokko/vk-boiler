package com.github.knokko.boiler.samples;

import com.github.knokko.boiler.builder.BoilerBuilder;
import com.github.knokko.boiler.builder.instance.ValidationFeatures;
import com.github.knokko.boiler.builder.xr.BoilerXrBuilder;
import com.github.knokko.boiler.commands.CommandRecorder;
import com.github.knokko.boiler.sync.WaitSemaphore;
import org.joml.Matrix4f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.openxr.*;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static com.github.knokko.boiler.util.CollectionHelper.createSet;
import static com.github.knokko.boiler.xr.OpenXrFailureException.assertXrSuccess;
import static java.lang.Thread.sleep;
import static org.lwjgl.openxr.XR10.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRCreateRenderpass2.VK_KHR_CREATE_RENDERPASS_2_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRDepthStencilResolve.VK_KHR_DEPTH_STENCIL_RESOLVE_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRDynamicRendering.*;
import static org.lwjgl.vulkan.KHRGetPhysicalDeviceProperties2.VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRGetPhysicalDeviceProperties2.vkGetPhysicalDeviceFeatures2KHR;
import static org.lwjgl.vulkan.KHRMaintenance2.VK_KHR_MAINTENANCE_2_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRMultiview.VK_KHR_MULTIVIEW_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK13.*;

public class HelloXR {

    public static void main(String[] args) throws InterruptedException {
        var boiler = new BoilerBuilder(
                VK_API_VERSION_1_0, "HelloXR", 1
        )
                .validation(new ValidationFeatures(false, false, false, true, true))
                .requiredVkInstanceExtensions(createSet(VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME))
                .requiredDeviceExtensions(createSet(
                        VK_KHR_DYNAMIC_RENDERING_EXTENSION_NAME,
                        VK_KHR_MULTIVIEW_EXTENSION_NAME,
                        VK_KHR_DEPTH_STENCIL_RESOLVE_EXTENSION_NAME,
                        VK_KHR_CREATE_RENDERPASS_2_EXTENSION_NAME,
                        VK_KHR_MAINTENANCE_2_EXTENSION_NAME
                ))
                .extraDeviceRequirements((physicalDevice, windowSurface, stack) -> {
                    var dynamicRendering = VkPhysicalDeviceDynamicRenderingFeaturesKHR.calloc(stack);
                    dynamicRendering.sType$Default();

                    var features2 = VkPhysicalDeviceFeatures2KHR.calloc(stack);
                    features2.sType$Default();
                    features2.pNext(dynamicRendering);

                    vkGetPhysicalDeviceFeatures2KHR(physicalDevice, features2);
                    return dynamicRendering.dynamicRendering();
                })
                .beforeDeviceCreation((ciDevice, physicalDevice, stack) -> {
                    var dynamicRendering = VkPhysicalDeviceDynamicRenderingFeaturesKHR.calloc(stack);
                    dynamicRendering.sType$Default();
                    dynamicRendering.dynamicRendering(true);

                    ciDevice.pNext(dynamicRendering);
                })
                .xr(new BoilerXrBuilder())
                .build();

        var session = boiler.xr().createSession(0, null);

        long swapchainFormat;
        int depthFormat;

        XrSwapchain swapchain;
        XrSpace renderSpace;
        int width, height;
        try (var stack = stackPush()) {
            swapchainFormat = boiler.xr().chooseSwapchainFormat(
                    stack, session,
                    VK_FORMAT_R8G8B8_SRGB, VK_FORMAT_B8G8R8_SRGB,
                    VK_FORMAT_R8G8B8A8_SRGB, VK_FORMAT_B8G8R8A8_SRGB
            );
            depthFormat = boiler.images.chooseDepthStencilFormat(
                    stack, VK_FORMAT_X8_D24_UNORM_PACK32, VK_FORMAT_D24_UNORM_S8_UINT, VK_FORMAT_D32_SFLOAT
            );

            var views = boiler.xr().getViewConfigurationViews(stack, XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO, true);
            if (views.capacity() != 2) throw new UnsupportedOperationException("Expected 2 views, but got " + views.capacity());

            width = views.recommendedImageRectWidth();
            height = views.recommendedImageRectHeight();

            var ciSwapchain = XrSwapchainCreateInfo.calloc(stack);
            ciSwapchain.type$Default();
            ciSwapchain.createFlags(0);
            ciSwapchain.usageFlags(XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT);
            ciSwapchain.format(swapchainFormat);
            ciSwapchain.sampleCount(1);
            ciSwapchain.width(width);
            ciSwapchain.height(height);
            ciSwapchain.sampleCount(1);
            ciSwapchain.faceCount(1);
            ciSwapchain.arraySize(2);
            ciSwapchain.mipCount(1);

            var pSwapchain = stack.callocPointer(1);
            assertXrSuccess(xrCreateSwapchain(
                    session, ciSwapchain, pSwapchain
            ), "CreateSwapchain", null);
            swapchain = new XrSwapchain(pSwapchain.get(0), session);
            renderSpace = boiler.xr().createReferenceSpace(stack, session, XR_REFERENCE_SPACE_TYPE_STAGE);
        }

        long[] swapchainImages = boiler.xr().getSwapchainImages(swapchain);
        long[] swapchainImageViews = new long[swapchainImages.length];
        try (var stack = stackPush()) {
            for (int index = 0; index < swapchainImages.length; index++) {
                swapchainImageViews[index] = boiler.images.createView(
                        stack, swapchainImages[index], (int) swapchainFormat,
                        VK_IMAGE_ASPECT_COLOR_BIT, 1, 2, "SwapchainView"
                );
            }
        }

        var commandPool = boiler.commands.createPool(
                VK_COMMAND_POOL_CREATE_TRANSIENT_BIT, boiler.queueFamilies().graphics().index(), "Drawing"
        );
        var commandBuffer = boiler.commands.createPrimaryBuffers(commandPool, 1, "Drawing")[0];
        var fence = boiler.sync.createFences(false, 1, "Drawing")[0];

        int[] sessionState = { XR_SESSION_STATE_IDLE };
        boolean isSessionRunning = false;
        boolean didRequestExit = false;
        boolean wantsToStop = false;

        Matrix4f[] lastCameraMatrix = { new Matrix4f(), new Matrix4f() };

        // Exit after 10 seconds
        long endTime = System.currentTimeMillis() + 10_000;
        while (true) {
            if (System.currentTimeMillis() > endTime) wantsToStop = true;

            try (var stack = stackPush()) {
                boiler.xr().pollEvents(stack, null, eventData -> {
                    if (eventData.type() == XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED) {
                        sessionState[0] = XrEventDataSessionStateChanged.nstate(eventData.address());
                        System.out.println("new session state is " + sessionState[0]);
                    }
                });

                if ((didRequestExit && !isSessionRunning)
                        || sessionState[0] == XR_SESSION_STATE_EXITING || sessionState[0] == XR_SESSION_STATE_LOSS_PENDING
                ) {
                    assertXrSuccess(xrDestroySession(session), "DestroySession", null);
                    break;
                }

                if (sessionState[0] == XR_SESSION_STATE_STOPPING) {
                    assertXrSuccess(xrEndSession(session), "EndSession", null);
                    isSessionRunning = false;
                    continue;
                }

                if (sessionState[0] == XR_SESSION_STATE_IDLE) {
                    sleep(500);
                    continue;
                }

                if (isSessionRunning && !didRequestExit && wantsToStop) {
                    assertXrSuccess(xrRequestExitSession(session), "RequestExitSession", null);
                    didRequestExit = true;
                    continue;
                }

                if (sessionState[0] == XR_SESSION_STATE_READY && !isSessionRunning) {
                    var biSession = XrSessionBeginInfo.calloc(stack);
                    biSession.type$Default();
                    // TODO Make this configurable
                    biSession.primaryViewConfigurationType(XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO);

                    assertXrSuccess(xrBeginSession(
                            session, biSession
                    ), "BeginSession", null);
                    isSessionRunning = true;
                    continue;
                }

                if (sessionState[0] == XR_SESSION_STATE_SYNCHRONIZED || sessionState[0] == XR_SESSION_STATE_VISIBLE ||
                    sessionState[0] == XR_SESSION_STATE_FOCUSED || sessionState[0] == XR_SESSION_STATE_READY
                ) {
                    var frameState = XrFrameState.calloc(stack);
                    frameState.type$Default();

                    assertXrSuccess(xrWaitFrame(
                            session, null, frameState
                    ), "WaitFrame", null);
                    assertXrSuccess(xrBeginFrame(session, null), "BeginFrame", null);

                    PointerBuffer layers = null;
                    if (frameState.shouldRender()) {
                        var projectionViews = boiler.xr().createProjectionViews(
                                stack, session, renderSpace, 2, XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO, // TODO Configure
                                frameState.predictedDisplayTime(), (subImage, index) -> {
                                    subImage.swapchain(swapchain);
                                    subImage.imageRect().offset().set(0, 0);
                                    subImage.imageRect().extent().set(width, height);
                                    subImage.imageArrayIndex(index);
                                }
                        );

                        var layer = XrCompositionLayerProjection.calloc(stack);
                        layer.type$Default();
                        layer.layerFlags(0); // TODO Maybe alpha
                        layer.space(renderSpace);
                        if (projectionViews != null) {
                            layer.views(projectionViews);
                            layers = stack.pointers(layer);
                        }

                        IntBuffer pImageIndex = stack.callocInt(1);
                        assertXrSuccess(xrAcquireSwapchainImage(
                                swapchain, null, pImageIndex
                        ), "AcquireSwapchainImage", null);
                        int swapchainImageIndex = pImageIndex.get(0);

                        Matrix4f[] cameraMatrix = { lastCameraMatrix[0], lastCameraMatrix[1] };
                        if (projectionViews != null) {
                            for (int index = 0; index < cameraMatrix.length; index++) {
                                // If the position tracker is working, we should use it to create the camera matrix
                                XrCompositionLayerProjectionView projectionView = projectionViews.get(index);

                                Matrix4f projectionMatrix = boiler.xr().createProjectionMatrix(
                                        projectionView.fov(), 0.01f, 100f
                                );

                                Matrix4f viewMatrix = new Matrix4f();

                                XrVector3f position = projectionView.pose().position$();
                                XrQuaternionf orientation = projectionView.pose().orientation();

                                viewMatrix.translationRotateScaleInvert(
                                        position.x(), position.y(), position.z(),
                                        orientation.x(), orientation.y(), orientation.z(), orientation.w(),
                                        1, 1, 1
                                );

                                cameraMatrix[index] = projectionMatrix.mul(viewMatrix);
                            }
                        }

                        lastCameraMatrix = cameraMatrix;

                        var wiSwapchain = XrSwapchainImageWaitInfo.calloc(stack);
                        wiSwapchain.type$Default();
                        wiSwapchain.timeout(1_000_000_000L); // TODO Configure

                        assertXrSuccess(xrWaitSwapchainImage(
                                swapchain, wiSwapchain
                        ), "WaitSwapchainImage", null);

                        var colorAttachments = VkRenderingAttachmentInfoKHR.calloc(1, stack);
                        colorAttachments.sType$Default();
                        colorAttachments.imageView(swapchainImageViews[swapchainImageIndex]);
                        colorAttachments.imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
                        colorAttachments.resolveMode(VK_RESOLVE_MODE_NONE);
                        colorAttachments.loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR);
                        colorAttachments.storeOp(VK_ATTACHMENT_STORE_OP_STORE);
                        colorAttachments.clearValue().color().float32(stack.floats(1f, 0f, 0f, 1f));

                        var dynamicRenderingInfo = VkRenderingInfoKHR.calloc(stack);
                        dynamicRenderingInfo.sType$Default();
                        dynamicRenderingInfo.renderArea().offset().set(0, 0);
                        dynamicRenderingInfo.renderArea().extent().set(width, height);
                        dynamicRenderingInfo.layerCount(2);
                        dynamicRenderingInfo.viewMask(0);
                        dynamicRenderingInfo.pColorAttachments(colorAttachments);

                        var commands = CommandRecorder.begin(commandBuffer, boiler, stack, "Drawing");
                        vkCmdBeginRenderingKHR(commandBuffer, dynamicRenderingInfo);
                        vkCmdEndRenderingKHR(commandBuffer);
                        commands.end();

                        boiler.queueFamilies().graphics().queues().get(0).submit(
                                commandBuffer, "Drawing", new WaitSemaphore[0], fence
                        );

                        assertVkSuccess(vkWaitForFences(
                                boiler.vkDevice(), stack.longs(fence), true, 1_000_000_000L // TODO Configure
                        ), "WaitForFences", "Drawing");
                        assertVkSuccess(vkResetFences(
                                boiler.vkDevice(), stack.longs(fence)
                        ), "ResetFences", "Drawing");
                        assertVkSuccess(vkResetCommandPool(
                                boiler.vkDevice(), commandPool, 0
                        ), "ResetCommandPool", "Drawing");
                        assertXrSuccess(xrReleaseSwapchainImage(
                                swapchain, null
                        ), "ReleaseSwapchainImage", null);
                    }

                    var frameEnd = XrFrameEndInfo.calloc(stack);
                    frameEnd.type$Default();
                    frameEnd.displayTime(frameState.predictedDisplayTime());
                    frameEnd.environmentBlendMode(XR_ENVIRONMENT_BLEND_MODE_OPAQUE); // TODO Configure
                    frameEnd.layerCount(layers != null ? layers.remaining() : 0);
                    frameEnd.layers(layers);

                    assertXrSuccess(xrEndFrame(session, frameEnd), "EndFrame", null);
                }
            }
        }

        vkDestroyFence(boiler.vkDevice(), fence, null);
        vkDestroyCommandPool(boiler.vkDevice(), commandPool, null);
        for (long imageView : swapchainImageViews) {
            vkDestroyImageView(boiler.vkDevice(), imageView, null);
        }

        xrDestroySpace(renderSpace);
        xrDestroySwapchain(swapchain);

        boiler.destroyInitialObjects();
    }
}
