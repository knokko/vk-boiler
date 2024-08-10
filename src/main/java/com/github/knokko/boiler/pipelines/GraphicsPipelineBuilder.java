package com.github.knokko.boiler.pipelines;

import com.github.knokko.boiler.instance.BoilerInstance;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.util.ArrayList;
import java.util.List;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.vulkan.VK10.*;

public class GraphicsPipelineBuilder {

    public final VkGraphicsPipelineCreateInfo ciPipeline;
    private final BoilerInstance boiler;
    private final MemoryStack stack;

    private final List<Long> shaderModules = new ArrayList<>();

    public long pipelineCache = VK_NULL_HANDLE;

    public GraphicsPipelineBuilder(VkGraphicsPipelineCreateInfo ciPipeline, BoilerInstance boiler, MemoryStack stack) {
        this.ciPipeline = ciPipeline;
        this.boiler = boiler;
        this.stack = stack;
    }

    public GraphicsPipelineBuilder(BoilerInstance boiler, MemoryStack stack) {
        this.ciPipeline = VkGraphicsPipelineCreateInfo.calloc(stack);
        this.ciPipeline.sType$Default();
        this.boiler = boiler;
        this.stack = stack;
    }

    public void shaderStages(ShaderInfo... shaders) {
        var ciShaderStages = VkPipelineShaderStageCreateInfo.calloc(shaders.length, stack);
        for (int index = 0; index < shaders.length; index++) {
            var shader = shaders[index];
            var ciShader = ciShaderStages.get(index);
            ciShader.sType$Default();
            ciShader.stage(shader.stage());
            ciShader.module(shader.module());
            ciShader.pName(stack.UTF8("main"));
            ciShader.pSpecializationInfo(shader.specialization());
        }

        ciPipeline.stageCount(shaders.length);
        ciPipeline.pStages(ciShaderStages);
    }

    public void simpleShaderStages(String description, String vertexPath, String fragmentPath) {
        long vertexModule = boiler.pipelines.createShaderModule(stack, vertexPath, description + "-VertexShader");
        long fragmentModule = boiler.pipelines.createShaderModule(stack, fragmentPath, description + "-FragmentShader");
        shaderStages(
                new ShaderInfo(VK_SHADER_STAGE_VERTEX_BIT, vertexModule, null),
                new ShaderInfo(VK_SHADER_STAGE_FRAGMENT_BIT, fragmentModule, null)
        );
        shaderModules.add(vertexModule);
        shaderModules.add(fragmentModule);
    }

    public void noVertexInput() {
        var vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack);
        vertexInput.sType$Default();
        vertexInput.flags(0);
        vertexInput.pVertexBindingDescriptions(null);
        vertexInput.pVertexAttributeDescriptions(null);

        ciPipeline.pVertexInputState(vertexInput);
    }

    public void dynamicStates(int... dynamicStates) {
        var ciDynamic = VkPipelineDynamicStateCreateInfo.calloc(stack);
        ciDynamic.sType$Default();
        ciDynamic.pDynamicStates(stack.ints(dynamicStates));

        ciPipeline.pDynamicState(ciDynamic);
    }

    public void dynamicViewports(int numViewports) {
        var ciViewport = VkPipelineViewportStateCreateInfo.calloc(stack);
        ciViewport.sType$Default();
        ciViewport.viewportCount(numViewports);
        ciViewport.pViewports(null);
        ciViewport.scissorCount(numViewports);
        ciViewport.pScissors(null);

        ciPipeline.pViewportState(ciViewport);
    }

    public void fixedViewport(int width, int height) {
        var viewportSize = VkViewport.calloc(1, stack);
        viewportSize.x(0f);
        viewportSize.y(0f);
        viewportSize.width(width);
        viewportSize.height(height);
        viewportSize.minDepth(0f);
        viewportSize.maxDepth(1f);

        var scissorSize = VkRect2D.calloc(1, stack);
        scissorSize.offset().set(0, 0);
        scissorSize.extent().set(width, height);

        var ciViewport = VkPipelineViewportStateCreateInfo.calloc(stack);
        ciViewport.sType$Default();
        ciViewport.viewportCount(1);
        ciViewport.pViewports(viewportSize);
        ciViewport.scissorCount(1);
        ciViewport.pScissors(scissorSize);

        ciPipeline.pViewportState(ciViewport);
    }

    public void simpleRasterization(int cullMode) {
        var ciRaster = VkPipelineRasterizationStateCreateInfo.calloc(stack);
        ciRaster.sType$Default();
        ciRaster.depthClampEnable(false);
        ciRaster.rasterizerDiscardEnable(false);
        ciRaster.polygonMode(VK_POLYGON_MODE_FILL);
        ciRaster.cullMode(cullMode);
        ciRaster.frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE);
        ciRaster.depthBiasEnable(false);
        ciRaster.lineWidth(1f);

        ciPipeline.pRasterizationState(ciRaster);
    }

    public void simpleInputAssembly() {
        var ciInputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack);
        ciInputAssembly.sType$Default();
        ciInputAssembly.topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
        ciInputAssembly.primitiveRestartEnable(false);

        ciPipeline.pInputAssemblyState(ciInputAssembly);
    }

    public void noMultisampling() {
        var ciMultisample = VkPipelineMultisampleStateCreateInfo.calloc(stack);
        ciMultisample.sType$Default();
        ciMultisample.rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
        ciMultisample.sampleShadingEnable(false);

        ciPipeline.pMultisampleState(ciMultisample);
    }

    public void noDepthStencil() {
        var ciDepthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack);
        ciDepthStencil.sType$Default();
        ciDepthStencil.depthTestEnable(false);
        ciDepthStencil.depthWriteEnable(false);
        ciDepthStencil.depthCompareOp(VK_COMPARE_OP_ALWAYS);
        ciDepthStencil.depthBoundsTestEnable(false);
        ciDepthStencil.stencilTestEnable(false);

        ciPipeline.pDepthStencilState(ciDepthStencil);
    }

    public void simpleDepthStencil(int compareOp) {
        var ciDepthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack);
        ciDepthStencil.sType$Default();
        ciDepthStencil.depthTestEnable(true);
        ciDepthStencil.depthWriteEnable(true);
        ciDepthStencil.depthCompareOp(compareOp);
        ciDepthStencil.depthBoundsTestEnable(false);
        ciDepthStencil.stencilTestEnable(false);

        ciPipeline.pDepthStencilState(ciDepthStencil);
    }

    public void noColorBlending(int attachmentCount) {
        var pAttachments = VkPipelineColorBlendAttachmentState.calloc(attachmentCount, stack);
        for (int index = 0; index < attachmentCount; index++) {
            var attachment = pAttachments.get(index);
            attachment.blendEnable(false);
            attachment.colorWriteMask(
                    VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT |
                            VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT
            );
        }

        var ciColorBlend = VkPipelineColorBlendStateCreateInfo.calloc(stack);
        ciColorBlend.sType$Default();
        ciColorBlend.logicOpEnable(false);
        ciColorBlend.attachmentCount(attachmentCount);
        ciColorBlend.pAttachments(pAttachments);

        ciPipeline.pColorBlendState(ciColorBlend);
    }

    public void simpleColorBlending(int attachmentCount) {
        var attachments = VkPipelineColorBlendAttachmentState.calloc(attachmentCount, stack);
        for (int index = 0; index < attachmentCount; index++) {
            var attachment = attachments.get(index);
            attachment.blendEnable(true);
            attachment.srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA);
            attachment.dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA);
            attachment.colorBlendOp(VK_BLEND_OP_ADD);
            attachment.srcAlphaBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA);
            attachment.dstAlphaBlendFactor(VK_BLEND_FACTOR_DST_ALPHA);
            attachment.alphaBlendOp(VK_BLEND_OP_MAX);
            attachment.colorWriteMask(
                    VK_COLOR_COMPONENT_R_BIT |
                            VK_COLOR_COMPONENT_G_BIT |
                            VK_COLOR_COMPONENT_B_BIT |
                            VK_COLOR_COMPONENT_A_BIT
            );
        }

        var ciColorBlend = VkPipelineColorBlendStateCreateInfo.calloc(stack);
        ciColorBlend.sType$Default();
        ciColorBlend.logicOpEnable(false);
        ciColorBlend.pAttachments(attachments);

        ciPipeline.pColorBlendState(ciColorBlend);
    }

    public long build(String name) {
        var pPipeline = stack.callocLong(1);
        assertVkSuccess(vkCreateGraphicsPipelines(
                boiler.vkDevice(), pipelineCache,
                VkGraphicsPipelineCreateInfo.create(ciPipeline.address(), 1),
                null, pPipeline
        ), "CreateGraphicsPipelines", name);
        long pipeline = pPipeline.get(0);
        boiler.debug.name(stack, pipeline, VK_OBJECT_TYPE_PIPELINE, name);

        for (long module : shaderModules) {
            vkDestroyShaderModule(boiler.vkDevice(), module, null);
        }

        return pipeline;
    }
}
