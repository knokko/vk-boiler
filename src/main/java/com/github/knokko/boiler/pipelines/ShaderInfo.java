package com.github.knokko.boiler.pipelines;

import org.lwjgl.vulkan.VkSpecializationInfo;

/**
 * This record is a simple tuple to store the information needed to assign a shader module to a pipeline.
 * @param stage The <i>VkShaderStageFlagBits</i>
 * @param module The <i>VkShaderModule</i>
 * @param specialization The optional specialization constant info
 */
public record ShaderInfo(int stage, long module, VkSpecializationInfo specialization) {
}
