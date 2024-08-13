package com.github.knokko.boiler.samples;

import com.github.knokko.boiler.builder.BoilerBuilder;
import com.github.knokko.boiler.builder.xr.BoilerXrBuilder;
import com.github.knokko.boiler.commands.CommandRecorder;
import com.github.knokko.boiler.descriptors.DescriptorSetLayout;
import com.github.knokko.boiler.descriptors.HomogeneousDescriptorPool;
import com.github.knokko.boiler.images.VmaImage;
import com.github.knokko.boiler.pipelines.GraphicsPipelineBuilder;
import com.github.knokko.boiler.pipelines.ShaderInfo;
import com.github.knokko.boiler.sync.WaitSemaphore;
import com.github.knokko.boiler.xr.BoilerSession;
import com.github.knokko.boiler.xr.SessionLoop;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.openxr.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static com.github.knokko.boiler.util.CollectionHelper.createSet;
import static com.github.knokko.boiler.xr.OpenXrFailureException.assertXrSuccess;
import static org.lwjgl.openxr.XR10.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memFloatBuffer;
import static org.lwjgl.system.MemoryUtil.memIntBuffer;
import static org.lwjgl.util.vma.Vma.vmaDestroyImage;
import static org.lwjgl.vulkan.KHRCreateRenderpass2.VK_KHR_CREATE_RENDERPASS_2_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRDepthStencilResolve.VK_KHR_DEPTH_STENCIL_RESOLVE_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRDynamicRendering.*;
import static org.lwjgl.vulkan.KHRGetPhysicalDeviceProperties2.VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRGetPhysicalDeviceProperties2.vkGetPhysicalDeviceFeatures2KHR;
import static org.lwjgl.vulkan.KHRMaintenance2.VK_KHR_MAINTENANCE_2_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRMultiview.VK_KHR_MULTIVIEW_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK13.*;

public class HelloXR {

    @SuppressWarnings("resource")
    public static void main(String[] args) throws InterruptedException {
        // TODO Shorten this sample using newer vk-boiler features
        var boiler = new BoilerBuilder(
                VK_API_VERSION_1_0, "HelloXR", 1
        )
                .validation()
                .requiredVkInstanceExtensions(createSet(VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME))
                .requiredDeviceExtensions(createSet(
                        VK_KHR_DYNAMIC_RENDERING_EXTENSION_NAME,
                        VK_KHR_MULTIVIEW_EXTENSION_NAME,
                        VK_KHR_DEPTH_STENCIL_RESOLVE_EXTENSION_NAME,
                        VK_KHR_CREATE_RENDERPASS_2_EXTENSION_NAME,
                        VK_KHR_MAINTENANCE_2_EXTENSION_NAME
                ))
                .printDeviceRejectionInfo()
                .extraDeviceRequirements((physicalDevice, windowSurface, stack) -> {
                    var dynamicRendering = VkPhysicalDeviceDynamicRenderingFeaturesKHR.calloc(stack);
                    dynamicRendering.sType$Default();

                    var multiview = VkPhysicalDeviceMultiviewFeaturesKHR.calloc(stack);
                    multiview.sType$Default();

                    var features2 = VkPhysicalDeviceFeatures2KHR.calloc(stack);
                    features2.sType$Default();
                    features2.pNext(dynamicRendering);
                    features2.pNext(multiview);

                    vkGetPhysicalDeviceFeatures2KHR(physicalDevice, features2);
                    return dynamicRendering.dynamicRendering() && multiview.multiview();
                })
                .beforeDeviceCreation((ciDevice, instanceExtensions, physicalDevice, stack) -> {
                    var dynamicRendering = VkPhysicalDeviceDynamicRenderingFeaturesKHR.calloc(stack);
                    dynamicRendering.sType$Default();
                    dynamicRendering.dynamicRendering(true);

                    var multiview = VkPhysicalDeviceMultiviewFeaturesKHR.calloc(stack);
                    multiview.sType$Default();
                    multiview.multiview(true);

                    ciDevice.pNext(dynamicRendering);
                    ciDevice.pNext(multiview);
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
            swapchainFormat = session.chooseSwapchainFormat(
                    stack, VK_FORMAT_R8G8B8_SRGB, VK_FORMAT_B8G8R8_SRGB,
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
                    session.session, ciSwapchain, pSwapchain
            ), "CreateSwapchain", null);
            swapchain = new XrSwapchain(pSwapchain.get(0), session.session);
            renderSpace = session.createReferenceSpace(stack, XR_REFERENCE_SPACE_TYPE_STAGE);
        }

        long[] swapchainImages = boiler.xr().getSwapchainImages(swapchain);
        long[] swapchainImageViews = new long[swapchainImages.length];
        VmaImage depthImage;
        try (var stack = stackPush()) {
            for (int index = 0; index < swapchainImages.length; index++) {
                swapchainImageViews[index] = boiler.images.createView(
                        stack, swapchainImages[index], (int) swapchainFormat,
                        VK_IMAGE_ASPECT_COLOR_BIT, 1, 2, "SwapchainView"
                );
            }
            depthImage = boiler.images.create(
                    stack, width, height, depthFormat, VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT,
                    VK_IMAGE_ASPECT_DEPTH_BIT, VK_SAMPLE_COUNT_1_BIT, 1, 2, true, "DepthImage"
            );
        }

        var commandPool = boiler.commands.createPool(
                VK_COMMAND_POOL_CREATE_TRANSIENT_BIT, boiler.queueFamilies().graphics().index(), "Drawing"
        );
        var commandBuffer = boiler.commands.createPrimaryBuffers(commandPool, 1, "Drawing")[0];
        var fence = boiler.sync.fenceBank.borrowFence(false, "Drawing");

        int vertexSize = (3 + 3) * 4;
        var vertexBuffer = boiler.buffers.createMapped(
                4 * vertexSize, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, "VertexBuffer"
        );
        var hostVertexBuffer = memFloatBuffer(vertexBuffer.hostAddress(), 4 * 6);
        hostVertexBuffer.put(-1f).put(0f).put(1f); // vertex1.position = (-1, 0, 1)
        hostVertexBuffer.put(1f).put(0f).put(0f); // vertex1.color = red
        hostVertexBuffer.put(1f).put(0f).put(1f); // vertex2.position = (1, 0, 1)
        hostVertexBuffer.put(0f).put(1f).put(0f); // vertex2.color = green
        hostVertexBuffer.put(0f).put(0f).put(-1f); // vertex3.position = (0, 0, -1)
        hostVertexBuffer.put(0f).put(0f).put(1f); // vertex3.color = blue
        hostVertexBuffer.put(0f).put(1f).put(0f); // vertex4.position = (0, 1, 0)
        hostVertexBuffer.put(0.5f).put(0.5f).put(0.5f); // vertex4.color = grey

        var indexBuffer = boiler.buffers.createMapped(
                5 * 3 * 4, VK_BUFFER_USAGE_INDEX_BUFFER_BIT, "IndexBuffer"
        );
        var hostIndexBuffer = memIntBuffer(indexBuffer.hostAddress(), 5 * 3);
        hostIndexBuffer.put(0).put(1).put(2); // bottom triangle, pointing up
        hostIndexBuffer.put(2).put(1).put(0); // bottom triangle, pointing down
        hostIndexBuffer.put(0).put(1).put(3); // back of the hand triangle
        hostIndexBuffer.put(1).put(2).put(3); // right of the hand triangle
        hostIndexBuffer.put(2).put(0).put(3); // left of the hand triangle

        var matrixBuffer = boiler.buffers.createMapped(
                5 * 64, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, "MatrixBuffer"
        );

        DescriptorSetLayout descriptorSetLayout;
        HomogeneousDescriptorPool descriptorPool;
        long descriptorSet;
        long pipelineLayout;
        long graphicsPipeline;
        try (var stack = stackPush()) {

            var layoutBindings = VkDescriptorSetLayoutBinding.calloc(1, stack);
            var matricesBinding = layoutBindings.get(0);
            matricesBinding.binding(0);
            matricesBinding.descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
            matricesBinding.descriptorCount(1);
            matricesBinding.stageFlags(VK_SHADER_STAGE_VERTEX_BIT);
            matricesBinding.pImmutableSamplers(null);

            descriptorSetLayout = boiler.descriptors.createLayout(stack, layoutBindings, "MatricesLayout");
            descriptorPool = descriptorSetLayout.createPool(1, 0, "MatricesPool");
            descriptorSet = descriptorPool.allocate(stack, 1)[0];

            var descriptorWrites = VkWriteDescriptorSet.calloc(1, stack);
            boiler.descriptors.writeBuffer(stack, descriptorWrites, descriptorSet, 0, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, matrixBuffer);

            vkUpdateDescriptorSets(boiler.vkDevice(), descriptorWrites, null);

            var pushConstants = VkPushConstantRange.calloc(1, stack);
            pushConstants.stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT);
            pushConstants.offset(0);
            pushConstants.size(8);

            pipelineLayout = boiler.pipelines.createLayout(
                    stack, pushConstants, "SimplePipelineLayout", descriptorSetLayout.vkDescriptorSetLayout
            );
            var vertexShader = boiler.pipelines.createShaderModule(
                    stack, "com/github/knokko/boiler/samples/graphics/xr.vert.spv", "VertexShader"
            );
            var fragmentShader = boiler.pipelines.createShaderModule(
                    stack, "com/github/knokko/boiler/samples/graphics/xr.frag.spv", "FragmentShader"
            );

            var vertexBindings = VkVertexInputBindingDescription.calloc(1, stack);
            vertexBindings.binding(0);
            vertexBindings.stride(vertexSize);
            vertexBindings.inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

            var vertexAttributes = VkVertexInputAttributeDescription.calloc(2, stack);
            var positionAttribute = vertexAttributes.get(0);
            positionAttribute.location(0);
            positionAttribute.binding(0);
            positionAttribute.format(VK_FORMAT_R32G32B32_SFLOAT);
            positionAttribute.offset(0);
            var colorAttribute = vertexAttributes.get(1);
            colorAttribute.location(1);
            colorAttribute.binding(0);
            colorAttribute.format(VK_FORMAT_R32G32B32_SFLOAT);
            colorAttribute.offset(12);

            var ciVertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack);
            ciVertexInput.sType$Default();
            ciVertexInput.pVertexBindingDescriptions(vertexBindings);
            ciVertexInput.pVertexAttributeDescriptions(vertexAttributes);

            var dynamicRendering = VkPipelineRenderingCreateInfoKHR.calloc(stack);
            dynamicRendering.sType$Default();
            dynamicRendering.viewMask(3);
            dynamicRendering.colorAttachmentCount(1);
            dynamicRendering.pColorAttachmentFormats(stack.ints((int) swapchainFormat));
            dynamicRendering.depthAttachmentFormat(depthFormat);
            dynamicRendering.stencilAttachmentFormat(VK_FORMAT_UNDEFINED);

            var pipelineBuilder = new GraphicsPipelineBuilder(boiler, stack);
            pipelineBuilder.shaderStages(
                    new ShaderInfo(VK_SHADER_STAGE_VERTEX_BIT, vertexShader, null),
                    new ShaderInfo(VK_SHADER_STAGE_FRAGMENT_BIT, fragmentShader, null)
            );
            pipelineBuilder.ciPipeline.pVertexInputState(ciVertexInput);
            pipelineBuilder.simpleInputAssembly();
            pipelineBuilder.fixedViewport(width, height);
            pipelineBuilder.simpleRasterization(VK_CULL_MODE_BACK_BIT);
            pipelineBuilder.noMultisampling();
            pipelineBuilder.simpleDepthStencil(VK_COMPARE_OP_LESS_OR_EQUAL);
            pipelineBuilder.ciPipeline.layout(pipelineLayout);
            pipelineBuilder.noColorBlending(1);
            pipelineBuilder.ciPipeline.pNext(dynamicRendering);

            graphicsPipeline = pipelineBuilder.build("SimplePipeline");

            vkDestroyShaderModule(boiler.vkDevice(), vertexShader, null);
            vkDestroyShaderModule(boiler.vkDevice(), fragmentShader, null);
        }

        XrActionSet actionSet;
        XrAction handPoseAction, handClickAction;
        long pathLeftHand, pathRightHand;
        XrSpace leftHandSpace, rightHandSpace;
        long interactionProfile;
        try (var stack = stackPush()) {
            actionSet = boiler.xr().actions.createSet(
                    stack, 1, "hands",
                    "Hand poses and click information"
            );
            pathLeftHand = boiler.xr().actions.getPath(stack, "/user/hand/left");
            pathRightHand = boiler.xr().actions.getPath(stack, "/user/hand/right");

            handPoseAction = boiler.xr().actions.createWithSubactions(
                    stack, actionSet, "hand_poses", "Hand poses",
                    XR_ACTION_TYPE_POSE_INPUT, pathLeftHand, pathRightHand
            );
            handClickAction = boiler.xr().actions.createWithSubactions(
                    stack, actionSet, "hand_clicks", "Clicking with hands",
                    XR_ACTION_TYPE_BOOLEAN_INPUT, pathLeftHand, pathRightHand
            );

            var pInteractionProfile = stack.callocLong(1);
            assertXrSuccess(xrStringToPath(
                    boiler.xr().instance, stack.UTF8("/interaction_profiles/khr/simple_controller"), pInteractionProfile
            ), "StringToPath", "Khronos simple controller interaction profile");
            interactionProfile = pInteractionProfile.get(0);

            var suggestedBindings = XrActionSuggestedBinding.calloc(4, stack);

            suggestedBindings.get(0).action(handPoseAction);
            suggestedBindings.get(0).binding(boiler.xr().actions.getPath(stack, "/user/hand/left/input/aim/pose"));

            suggestedBindings.get(1).action(handPoseAction);
            suggestedBindings.get(1).binding(boiler.xr().actions.getPath(stack, "/user/hand/right/input/aim/pose"));

            suggestedBindings.get(2).action(handClickAction);
            suggestedBindings.get(2).binding(boiler.xr().actions.getPath(stack, "/user/hand/left/input/select/click"));

            suggestedBindings.get(3).action(handClickAction);
            suggestedBindings.get(3).binding(boiler.xr().actions.getPath(stack, "/user/hand/right/input/select/click"));

            var suggestedInteractionBindings = XrInteractionProfileSuggestedBinding.calloc(stack);
            suggestedInteractionBindings.type$Default();
            suggestedInteractionBindings.interactionProfile(interactionProfile);
            suggestedInteractionBindings.suggestedBindings(suggestedBindings);

            assertXrSuccess(xrSuggestInteractionProfileBindings(
                    boiler.xr().instance, suggestedInteractionBindings
            ), "SuggestInteractionProfileBindings", null);

            leftHandSpace = session.createActionSpace(stack, handPoseAction, pathLeftHand, "left hand");
            rightHandSpace = session.createActionSpace(stack, handPoseAction, pathRightHand, "right hand");

            session.attach(stack, actionSet);
        }

        // Stop demo after 10 seconds
        long endTime = System.nanoTime() + 10_000_000_000L;

        class HelloSessionLoop extends SessionLoop {

            private Matrix4f leftHandMatrix, rightHandMatrix;

            public HelloSessionLoop(
                    BoilerSession session, XrSpace renderSpace,
                    XrSwapchain swapchain, int width, int height
            ) {
                super(session, renderSpace, swapchain, width, height);
            }

            @Override
            protected Matrix4f createProjectionMatrix(XrFovf fov) {
                return xr.createProjectionMatrix(fov, 0.01f, 100f);
            }

            @Override
            protected XrActionSet[] chooseActiveActionSets() {
                return new XrActionSet[] { actionSet };
            }

            @Override
            protected void update() {
                if (System.nanoTime() > endTime) requestExit();
            }

            @Override
            protected void handleEvent(XrEventDataBuffer event) {
                System.out.println("Handled event of type " + event.type() + ": new state is " + getState());
            }

            @Override
            protected void prepareRender(MemoryStack stack, XrFrameState frameState) {
                var leftLocation = XrSpaceLocation.calloc(stack);
                leftLocation.type$Default();

                var rightLocation = XrSpaceLocation.calloc(stack);
                rightLocation.type$Default();

                assertXrSuccess(xrLocateSpace(
                        leftHandSpace, renderSpace, frameState.predictedDisplayTime(), leftLocation
                ), "LocateSpace", "left hand");
                assertXrSuccess(xrLocateSpace(
                        rightHandSpace, renderSpace, frameState.predictedDisplayTime(), rightLocation
                ), "LocateSpace", "right hand");

                Vector3f leftPosition = null;
                Vector3f rightPosition = null;

                if ((leftLocation.locationFlags() & XR_SPACE_LOCATION_POSITION_VALID_BIT) != 0) {
                    var pos = leftLocation.pose().position$();
                    leftPosition = new Vector3f(pos.x(), pos.y(), pos.z());
                }
                if ((rightLocation.locationFlags() & XR_SPACE_LOCATION_POSITION_VALID_BIT) != 0) {
                    var pos = rightLocation.pose().position$();
                    rightPosition = new Vector3f(pos.x(), pos.y(), pos.z());
                }

                Quaternionf leftRotation = null;
                Quaternionf rightRotation = null;
                if ((leftLocation.locationFlags() & XR_SPACE_LOCATION_ORIENTATION_VALID_BIT) != 0) {
                    var rot = leftLocation.pose().orientation();
                    leftRotation = new Quaternionf(rot.x(), rot.y(), rot.z(), rot.w());
                }

                if ((rightLocation.locationFlags() & XR_SPACE_LOCATION_ORIENTATION_VALID_BIT) != 0) {
                    var rot = rightLocation.pose().orientation();
                    rightRotation = new Quaternionf(rot.x(), rot.y(), rot.z(), rot.w());
                }

                if (leftPosition != null) {
                    leftHandMatrix = new Matrix4f().translate(leftPosition);

                    if (leftRotation != null) {
                        leftHandMatrix.rotate(leftRotation);
                    }
                    leftHandMatrix.scale(0.1f);
                }

                if (rightPosition != null) {
                    rightHandMatrix = new Matrix4f().translate(rightPosition);

                    if (rightRotation != null) {
                        rightHandMatrix.rotate(rightRotation);
                    }
                    rightHandMatrix.scale(0.1f);
                }
            }

            @Override
            protected void recordRenderCommands(
                    MemoryStack stack, int swapchainImageIndex, Matrix4f[] cameraMatrices
            ) {
                var colorAttachments = VkRenderingAttachmentInfoKHR.calloc(1, stack);
                colorAttachments.sType$Default();
                colorAttachments.imageView(swapchainImageViews[swapchainImageIndex]);
                colorAttachments.imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
                colorAttachments.resolveMode(VK_RESOLVE_MODE_NONE);
                colorAttachments.loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR);
                colorAttachments.storeOp(VK_ATTACHMENT_STORE_OP_STORE);
                colorAttachments.clearValue().color().float32(stack.floats(1f, 0f, 0f, 1f));

                var depthAttachment = VkRenderingAttachmentInfoKHR.calloc(stack);
                depthAttachment.sType$Default();
                depthAttachment.imageView(depthImage.vkImageView());
                depthAttachment.imageLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
                depthAttachment.resolveMode(VK_RESOLVE_MODE_NONE);
                depthAttachment.loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR);
                depthAttachment.storeOp(VK_ATTACHMENT_STORE_OP_DONT_CARE);
                depthAttachment.clearValue().depthStencil().depth(1f);

                var dynamicRenderingInfo = VkRenderingInfoKHR.calloc(stack);
                dynamicRenderingInfo.sType$Default();
                dynamicRenderingInfo.renderArea().offset().set(0, 0);
                dynamicRenderingInfo.renderArea().extent().set(width, height);
                dynamicRenderingInfo.layerCount(2);
                dynamicRenderingInfo.viewMask(3);
                dynamicRenderingInfo.pColorAttachments(colorAttachments);
                dynamicRenderingInfo.pDepthAttachment(depthAttachment);

                var commands = CommandRecorder.begin(commandBuffer, xr.boiler, stack, "Drawing");
                vkCmdBeginRenderingKHR(commandBuffer, dynamicRenderingInfo);

                vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicsPipeline);
                vkCmdBindDescriptorSets(
                        commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS,
                        pipelineLayout, 0, stack.longs(descriptorSet), null
                );
                vkCmdBindVertexBuffers(
                        commandBuffer, 0,
                        stack.longs(vertexBuffer.vkBuffer()), stack.longs(0)
                );
                vkCmdBindIndexBuffer(commandBuffer, indexBuffer.vkBuffer(), 0, VK_INDEX_TYPE_UINT32);

                var hostMatrixBuffer = memFloatBuffer(matrixBuffer.hostAddress(), 5 * 16);
                cameraMatrices[0].get(0, hostMatrixBuffer);
                cameraMatrices[1].get(16, hostMatrixBuffer);
                new Matrix4f().get(32, hostMatrixBuffer);
                if (leftHandMatrix != null) leftHandMatrix.get(48, hostMatrixBuffer);
                if (rightHandMatrix != null) rightHandMatrix.get(64, hostMatrixBuffer);

                var pushConstants = stack.callocInt(2);

                pushConstants.put(0, 0);
                pushConstants.put(1, 0);
                vkCmdPushConstants(
                        commandBuffer, pipelineLayout,
                        VK_SHADER_STAGE_FRAGMENT_BIT | VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstants
                );
                vkCmdDrawIndexed(commandBuffer, 3, 1, 0, 0, 0);

                var pClick = XrActionStateBoolean.calloc(stack);
                pClick.type$Default();

                var giClick = XrActionStateGetInfo.calloc(stack);
                giClick.type$Default();
                giClick.action(handClickAction);
                giClick.subactionPath(pathLeftHand);
                if (leftHandMatrix != null) {
                    assertXrSuccess(xrGetActionStateBoolean(
                            session.session, giClick, pClick
                    ), "GetActionStateBoolean", "left click");

                    pushConstants.put(0, pClick.currentState() ? 0 : 1);
                    pushConstants.put(1, 1);
                    vkCmdPushConstants(
                            commandBuffer, pipelineLayout,
                            VK_SHADER_STAGE_FRAGMENT_BIT | VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstants
                    );
                    vkCmdDrawIndexed(commandBuffer, 12, 1, 3, 0, 0);
                }
                if (rightHandMatrix != null) {
                    giClick.subactionPath(pathRightHand);
                    assertXrSuccess(xrGetActionStateBoolean(
                            session.session, giClick, pClick
                    ), "GetActionStateBoolean", "right click");

                    pushConstants.put(0, pClick.currentState() ? 0 : 1);
                    pushConstants.put(1, 2);
                    vkCmdPushConstants(
                            commandBuffer, pipelineLayout,
                            VK_SHADER_STAGE_FRAGMENT_BIT | VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstants
                    );
                    vkCmdDrawIndexed(commandBuffer, 12, 1, 3, 0, 0);
                }

                vkCmdEndRenderingKHR(commandBuffer);
                commands.end();
            }

            @Override
            protected void submitAndWaitRender(MemoryStack stack) {
                xr.boiler.queueFamilies().graphics().queues().get(0).submit(
                        commandBuffer, "Drawing", new WaitSemaphore[0], fence
                );

                fence.waitAndReset(stack);
                assertVkSuccess(vkResetCommandPool(
                        xr.boiler.vkDevice(), commandPool, 0
                ), "ResetCommandPool", "Drawing");
            }
        }

        new HelloSessionLoop(session, renderSpace, swapchain, width, height).run();

        boiler.sync.fenceBank.returnFence(fence);
        vkDestroyCommandPool(boiler.vkDevice(), commandPool, null);
        vkDestroyPipeline(boiler.vkDevice(), graphicsPipeline, null);
        vkDestroyPipelineLayout(boiler.vkDevice(), pipelineLayout, null);
        descriptorPool.destroy();
        descriptorSetLayout.destroy();
        for (long imageView : swapchainImageViews) {
            vkDestroyImageView(boiler.vkDevice(), imageView, null);
        }

        vertexBuffer.destroy(boiler.vmaAllocator());
        indexBuffer.destroy(boiler.vmaAllocator());
        matrixBuffer.destroy(boiler.vmaAllocator());
        vkDestroyImageView(boiler.vkDevice(), depthImage.vkImageView(), null);
        vmaDestroyImage(boiler.vmaAllocator(), depthImage.vkImage(), depthImage.vmaAllocation());

        assertXrSuccess(xrDestroySpace(leftHandSpace), "DestroySpace", "left hand");
        assertXrSuccess(xrDestroySpace(rightHandSpace), "DestroySpace", "right hand");
        assertXrSuccess(xrDestroyAction(handPoseAction), "DestroyAction", "hand pose");
        assertXrSuccess(xrDestroyAction(handClickAction), "DestroyAction", "hand click");
        assertXrSuccess(xrDestroyActionSet(actionSet), "DestroyActionSet", null);

        assertXrSuccess(xrDestroySpace(renderSpace), "DestroySpace", "renderSpace");
        assertXrSuccess(xrDestroySwapchain(swapchain), "DestroySwapchain", null);

        assertXrSuccess(xrDestroySession(session.session), "DestroySession", null);

        boiler.destroyInitialObjects();
    }
}
