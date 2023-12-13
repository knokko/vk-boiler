package com.github.knokko.boiler.xr;

import com.github.knokko.boiler.instance.BoilerInstance;
import org.joml.Matrix4f;
import org.lwjgl.openxr.*;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;
import java.util.function.Consumer;

import static com.github.knokko.boiler.xr.OpenXrFailureException.assertXrSuccess;
import static org.joml.Math.tan;
import static org.lwjgl.openxr.XR10.*;
import static org.lwjgl.system.MemoryStack.stackPush;

public class XrBoiler {

    public final XrInstance instance;
    public final long system;

    public final BoilerActions actions = new BoilerActions(this);

    public BoilerInstance boiler;

    public XrBoiler(XrInstance xrInstance, long xrSystemId) {
        this.instance = xrInstance;
        this.system = xrSystemId;
    }

    public XrViewConfigurationView.Buffer getViewConfigurationViews(
            MemoryStack stack, int viewConfiguration, boolean requireIdenticalViews
    ) {
        IntBuffer pNumConfigs = stack.callocInt(1);
        assertXrSuccess(xrEnumerateViewConfigurations(
                instance, system, pNumConfigs, null
        ), "EnumerateViewConfigurations", "count");
        int numConfigs = pNumConfigs.get(0);

        IntBuffer pConfigs = stack.callocInt(numConfigs);
        assertXrSuccess(xrEnumerateViewConfigurations(
                instance, system, pNumConfigs, pConfigs
        ), "EnumerateViewConfigurations", "configs");

        boolean hasConfiguration = false;
        for (int index = 0; index < numConfigs; index++) {
            if (pConfigs.get(index) == viewConfiguration) hasConfiguration = true;
        }
        if (!hasConfiguration) throw new UnsupportedOperationException("View configuration is not supported");

        IntBuffer pNumViews = stack.callocInt(1);
        assertXrSuccess(xrEnumerateViewConfigurationViews(
                instance, system, viewConfiguration, pNumViews, null
        ), "EnumerateViewConfigurationViews", "count");
        int numViews = pNumViews.get(0);

        var pViews = XrViewConfigurationView.calloc(numViews, stack);
        for (int index = 0; index < numViews; index++) {
            //noinspection resource
            pViews.get(index).type$Default();
        }
        assertXrSuccess(xrEnumerateViewConfigurationViews(
                instance, system, viewConfiguration, pNumViews, pViews
        ), "EnumerateViewConfigurationViews", "views");

        if (requireIdenticalViews && numViews > 1) {
            var view1 = pViews.get(0);
            for (int index = 1; index < numViews; index++) {
                var view = pViews.get(1);
                if (
                        view.recommendedImageRectWidth() != view1.recommendedImageRectWidth() ||
                                view.recommendedImageRectHeight() != view1.recommendedImageRectHeight() ||
                                view.recommendedSwapchainSampleCount() != view1.recommendedSwapchainSampleCount() ||
                                view.maxImageRectWidth() != view1.maxImageRectWidth() ||
                                view.maxImageRectHeight() != view1.maxImageRectHeight() ||
                                view.maxSwapchainSampleCount() != view1.maxSwapchainSampleCount()
                ) {
                    throw new UnsupportedOperationException("View configuration views are not identical");
                }
            }
        }
        return pViews;
    }

    public long[] getSwapchainImages(XrSwapchain swapchain) {
        try (var stack = stackPush()) {
            var pNumImages = stack.callocInt(1);
            assertXrSuccess(xrEnumerateSwapchainImages(
                    swapchain, pNumImages, null
            ), "EnumerateSwapchainImages", "count");
            int numImages = pNumImages.get(0);

            var pImages = XrSwapchainImageVulkan2KHR.calloc(numImages, stack);
            for (int index = 0; index < numImages; index++) {
                //noinspection resource
                pImages.get(index).type$Default();
            }

            assertXrSuccess(xrEnumerateSwapchainImages(
                    swapchain, pNumImages, XrSwapchainImageBaseHeader.create(pImages.address(), numImages)
            ), "EnumerateSwapchainImages", "images");

            long[] result = new long[numImages];
            for (int index = 0; index < numImages; index++) {
                result[index] = pImages.get(index).image();
            }
            return result;
        }
    }

    public void setIdentity(XrPosef pose) {
        pose.orientation().set(0f, 0f, 0f, 1f);
        pose.position$().set(0f, 0f, 0f);
    }

    public Matrix4f createProjectionMatrix(XrFovf fov, float nearZ, float farZ) {
        // Most of this math is ripped from
        // https://github.com/LWJGL/lwjgl3/blob/92777ed4102ae8185df1042687306a587e7cd88b/modules/samples/src/test/java/org/lwjgl/demo/openxr/XRHelper.java#L98
        Matrix4f projectionMatrix = new Matrix4f().scale(1f, -1f, 1f);
        float distToLeftPlane   = tan(fov.angleLeft());
        float distToRightPlane  = tan(fov.angleRight());
        float distToBottomPlane = tan(fov.angleDown());
        float distToTopPlane    = tan(fov.angleUp());
        return projectionMatrix.frustum(
                distToLeftPlane * nearZ, distToRightPlane * nearZ,
                distToBottomPlane * nearZ, distToTopPlane * nearZ,
                nearZ, farZ, true
        );
    }

    public BoilerSession createSession(int queueIndex, String context) {
        try (var stack = stackPush()) {
            var vulkanBinding = XrGraphicsBindingVulkan2KHR.calloc(stack);
            vulkanBinding.type$Default();
            vulkanBinding.instance(boiler.vkInstance());
            vulkanBinding.physicalDevice(boiler.vkPhysicalDevice());
            vulkanBinding.device(boiler.vkDevice());
            vulkanBinding.queueFamilyIndex(boiler.queueFamilies().graphics().index());
            vulkanBinding.queueIndex(queueIndex);

            var ciSession = XrSessionCreateInfo.calloc(stack);
            ciSession.type$Default();
            ciSession.next(vulkanBinding.address());
            ciSession.createFlags(0);
            ciSession.systemId(system);

            var pSession = stack.callocPointer(1);
            assertXrSuccess(xrCreateSession(
                    instance, ciSession, pSession
            ), "CreateSession", context);
            return new BoilerSession(this, new XrSession(pSession.get(0), instance));
        }
    }

    public void pollEvents(MemoryStack stack, String context, Consumer<XrEventDataBuffer> processEvent) {
        var eventData = XrEventDataBuffer.malloc(stack);

        while (true) {
            eventData.type$Default();
            eventData.next(0L);

            int pollResult = xrPollEvent(instance, eventData);
            if (pollResult == XR_EVENT_UNAVAILABLE) return;
            assertXrSuccess(pollResult, "PollEvent", context);
            processEvent.accept(eventData);
        }
    }

    public void destroyInitialObjects() {
        assertXrSuccess(xrDestroyInstance(instance), "DestroyInstance", "XrBoiler");
    }
}
