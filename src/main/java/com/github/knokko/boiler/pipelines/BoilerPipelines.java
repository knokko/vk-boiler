package com.github.knokko.boiler.pipelines;

import com.github.knokko.boiler.instance.BoilerInstance;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.io.IOException;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.vulkan.VK10.*;

public class BoilerPipelines {

    private final BoilerInstance instance;

    public BoilerPipelines(BoilerInstance instance) {
        this.instance = instance;
    }

    public long createLayout(
            MemoryStack stack, VkPushConstantRange.Buffer pushConstants, String name, long... descriptorSetLayouts
    ) {
        var ciLayout = VkPipelineLayoutCreateInfo.calloc(stack);
        ciLayout.sType$Default();
        ciLayout.flags(0);
        ciLayout.setLayoutCount(1);
        ciLayout.pSetLayouts(stack.longs(descriptorSetLayouts));
        ciLayout.pPushConstantRanges(pushConstants);

        var pLayout = stack.callocLong(1);
        assertVkSuccess(vkCreatePipelineLayout(
                instance.vkDevice(), ciLayout, null, pLayout
        ), "CreatePipelineLayout", name);
        long layout = pLayout.get(0);

        instance.debug.name(stack, layout, VK_OBJECT_TYPE_PIPELINE_LAYOUT, name);
        return layout;
    }

    public long createShaderModule(MemoryStack stack, String resourcePath, String name) {
        var input = BoilerPipelines.class.getClassLoader().getResourceAsStream(resourcePath);
        if (input == null) throw new IllegalArgumentException("Can't find resource: " + resourcePath);
        byte[] inputArray;
        try {
            inputArray = input.readAllBytes();
            input.close();
        } catch (IOException shouldNotHappen) {
            throw new RuntimeException(shouldNotHappen);
        }

        var inputBuffer = memAlloc(inputArray.length);
        inputBuffer.put(0, inputArray);

        var ciModule = VkShaderModuleCreateInfo.calloc(stack);
        ciModule.sType$Default();
        ciModule.flags(0);
        ciModule.pCode(inputBuffer);

        var pModule = stack.callocLong(1);
        assertVkSuccess(vkCreateShaderModule(
                instance.vkDevice(), ciModule, null, pModule
        ), "CreateShaderModule", name);
        long module = pModule.get(0);

        instance.debug.name(stack, module, VK_OBJECT_TYPE_SHADER_MODULE, name);
        memFree(inputBuffer);
        return module;
    }

    public long createComputePipeline(
            MemoryStack stack, long pipelineLayout, String shaderPath, String name
    ) {
        long shaderModule = instance.pipelines.createShaderModule(stack, shaderPath, name + "-ShaderModule");

        var ciShaderStage = VkPipelineShaderStageCreateInfo.calloc(stack);
        ciShaderStage.sType$Default();
        ciShaderStage.flags(0);
        ciShaderStage.stage(VK_SHADER_STAGE_COMPUTE_BIT);
        ciShaderStage.module(shaderModule);
        ciShaderStage.pName(stack.UTF8("main"));

        var ciPipelines = VkComputePipelineCreateInfo.calloc(1, stack);
        var ciPipeline = ciPipelines.get(0);
        ciPipeline.sType$Default();
        ciPipeline.stage(ciShaderStage);
        ciPipeline.layout(pipelineLayout);

        var pPipelines = stack.callocLong(1);
        assertVkSuccess(vkCreateComputePipelines(
                instance.vkDevice(), VK_NULL_HANDLE, ciPipelines, null, pPipelines
        ), "CreateComputePipelines", name);
        long pipeline = pPipelines.get(0);

        instance.debug.name(stack, pipeline, VK_OBJECT_TYPE_PIPELINE, name);
        vkDestroyShaderModule(instance.vkDevice(), shaderModule, null);
        return pipeline;
    }

    public void shaderStages(
            MemoryStack stack, VkGraphicsPipelineCreateInfo ciPipeline, ShaderInfo... shaders
    ) {
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

    public void dynamicStates(MemoryStack stack, VkGraphicsPipelineCreateInfo ciPipeline, int... dynamicStates) {
        var ciDynamic = VkPipelineDynamicStateCreateInfo.calloc(stack);
        ciDynamic.sType$Default();
        ciDynamic.pDynamicStates(stack.ints(dynamicStates));

        ciPipeline.pDynamicState(ciDynamic);
    }

    public void dynamicViewports(MemoryStack stack, VkGraphicsPipelineCreateInfo ciPipeline, int numViewports) {
        var ciViewport = VkPipelineViewportStateCreateInfo.calloc(stack);
        ciViewport.sType$Default();
        ciViewport.viewportCount(numViewports);
        ciViewport.pViewports(null);
        ciViewport.scissorCount(numViewports);
        ciViewport.pScissors(null);

        ciPipeline.pViewportState(ciViewport);
    }

    public void simpleRasterization(MemoryStack stack, VkGraphicsPipelineCreateInfo ciPipeline, int cullMode) {
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

    public void simpleInputAssembly(MemoryStack stack, VkGraphicsPipelineCreateInfo ciPipeline) {
        var ciInputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack);
        ciInputAssembly.sType$Default();
        ciInputAssembly.topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
        ciInputAssembly.primitiveRestartEnable(false);

        ciPipeline.pInputAssemblyState(ciInputAssembly);
    }

    public void noMultisampling(MemoryStack stack, VkGraphicsPipelineCreateInfo ciPipeline) {
        var ciMultisample = VkPipelineMultisampleStateCreateInfo.calloc(stack);
        ciMultisample.sType$Default();
        ciMultisample.rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
        ciMultisample.sampleShadingEnable(false);

        ciPipeline.pMultisampleState(ciMultisample);
    }

    public void noDepthStencil(MemoryStack stack, VkGraphicsPipelineCreateInfo ciPipeline) {
        var ciDepthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack);
        ciDepthStencil.sType$Default();
        ciDepthStencil.depthTestEnable(false);
        ciDepthStencil.depthWriteEnable(false);
        ciDepthStencil.depthCompareOp(VK_COMPARE_OP_ALWAYS);
        ciDepthStencil.depthBoundsTestEnable(false);
        ciDepthStencil.stencilTestEnable(false);

        ciPipeline.pDepthStencilState(ciDepthStencil);
    }

    public void simpleDepthStencil(MemoryStack stack, VkGraphicsPipelineCreateInfo ciPipeline, int compareOp) {
        var ciDepthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack);
        ciDepthStencil.sType$Default();
        ciDepthStencil.depthTestEnable(true);
        ciDepthStencil.depthWriteEnable(true);
        ciDepthStencil.depthCompareOp(compareOp);
        ciDepthStencil.depthBoundsTestEnable(false);
        ciDepthStencil.stencilTestEnable(false);

        ciPipeline.pDepthStencilState(ciDepthStencil);
    }

    public void noColorBlending(MemoryStack stack, VkGraphicsPipelineCreateInfo ciPipeline, int attachmentCount) {
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

    public void simpleColorBlending(MemoryStack stack, VkGraphicsPipelineCreateInfo ciPipeline, int attachmentCount) {
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
}
