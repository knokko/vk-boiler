package com.github.knokko.boiler.synchronization;

import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.VK10.*;

/**
 * A tuple consisting of an imageLayout, access mask, and stage mask, used to describe pipeline barriers in vk-boiler.
 * This class has some constants that you can use to avoid the need to type all 3 properties out for each barrier.
 * @param imageLayout The image layout (only relevant for image memory barriers)
 * @param accessMask The <i>VkAccessFlagBits</i>
 * @param stageMask The <i>VkPipelineStageFlagBits</i>
 */
public record ResourceUsage(int imageLayout, int accessMask, int stageMask) {

	public static final ResourceUsage COLOR_ATTACHMENT_WRITE = new ResourceUsage(
			VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
			VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
			VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
	);

	public static final ResourceUsage TRANSFER_SOURCE = new ResourceUsage(
			VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, VK_ACCESS_TRANSFER_READ_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT
	);

	public static final ResourceUsage TRANSFER_DEST = new ResourceUsage(
			VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_ACCESS_TRANSFER_WRITE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT
	);

	public static final ResourceUsage PRESENT = new ResourceUsage(
			VK_IMAGE_LAYOUT_PRESENT_SRC_KHR, 0, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT
	);

	public static final ResourceUsage HOST_READ = new ResourceUsage(
			VK_IMAGE_LAYOUT_UNDEFINED, VK_ACCESS_HOST_READ_BIT, VK_PIPELINE_STAGE_HOST_BIT
	);

	public static ResourceUsage depthStencilAttachmentWrite(int imageLayout) {
		return new ResourceUsage(
				imageLayout, VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
				VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT
		);
	}

	public static ResourceUsage shaderRead(int stageMask) {
		return new ResourceUsage(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_ACCESS_SHADER_READ_BIT, stageMask);
	}

	public static ResourceUsage compute(int imageLayout, int accessMask) {
		return new ResourceUsage(imageLayout, accessMask, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT);
	}

	public static ResourceUsage computeBuffer(int accessMask) {
		return compute(0, accessMask);
	}

	public static ResourceUsage fromPresent(int stageMask) {
		return new ResourceUsage(VK_IMAGE_LAYOUT_UNDEFINED, 0, stageMask);
	}
}
