package com.github.knokko.boiler.commands;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.sync.ResourceUsage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.vulkan.KHRDynamicRendering.vkCmdBeginRenderingKHR;
import static org.lwjgl.vulkan.KHRDynamicRendering.vkCmdEndRenderingKHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.VK_RESOLVE_MODE_NONE;
import static org.lwjgl.vulkan.VK13.*;

public class CommandRecorder {

	public static CommandRecorder begin(
			VkCommandBuffer commandBuffer, BoilerInstance instance, MemoryStack stack, String context
	) {
		return begin(commandBuffer, instance, stack, 0, context);
	}

	public static CommandRecorder begin(
			VkCommandBuffer commandBuffer, BoilerInstance instance, MemoryStack stack, int flags, String context
	) {
		instance.commands.begin(commandBuffer, stack, flags, context);
		return new CommandRecorder(commandBuffer, instance, stack, context);
	}

	public static CommandRecorder alreadyRecording(
			VkCommandBuffer commandBuffer, BoilerInstance instance, MemoryStack stack
	) {
		return new CommandRecorder(commandBuffer, instance, stack, null);
	}

	public final VkCommandBuffer commandBuffer;
	private final BoilerInstance instance;
	private final MemoryStack stack;
	private final String context;

	private CommandRecorder(VkCommandBuffer commandBuffer, BoilerInstance instance, MemoryStack stack, String context) {
		this.commandBuffer = commandBuffer;
		this.instance = instance;
		this.stack = stack;
		this.context = context;
	}

	public void copyBuffer(
			long size, long vkSourceBuffer, long sourceOffset,
			long vkDestBuffer, long destOffset
	) {
		var copyRegion = VkBufferCopy.calloc(1, stack);
		copyRegion.srcOffset(sourceOffset);
		copyRegion.dstOffset(destOffset);
		copyRegion.size(size);

		vkCmdCopyBuffer(commandBuffer, vkSourceBuffer, vkDestBuffer, copyRegion);
	}

	public void copyImage(
			int width, int height, int aspectMask, long vkSourceImage, long vkDestImage
	) {
		var imageCopyRegions = VkImageCopy.calloc(1, stack);
		var copyRegion = imageCopyRegions.get(0);
		instance.images.subresourceLayers(copyRegion.srcSubresource(), aspectMask);
		copyRegion.srcOffset().set(0, 0, 0);
		instance.images.subresourceLayers(copyRegion.dstSubresource(), aspectMask);
		copyRegion.dstOffset().set(0, 0, 0);
		copyRegion.extent().set(width, height, 1);

		vkCmdCopyImage(
				commandBuffer, vkSourceImage, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
				vkDestImage, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, imageCopyRegions
		);
	}

	@SuppressWarnings("resource")
	public void blitImage(
			int aspectMask, int filter, long vkSourceImage, int sourceWidth, int sourceHeight,
			long vkDestImage, int destWidth, int destHeight
	) {
		var imageBlitRegions = VkImageBlit.calloc(1, stack);
		var blitRegion = imageBlitRegions.get(0);
		instance.images.subresourceLayers(blitRegion.srcSubresource(), aspectMask);
		blitRegion.srcOffsets().get(0).set(0, 0, 0);
		blitRegion.srcOffsets().get(1).set(sourceWidth, sourceHeight, 1);
		instance.images.subresourceLayers(blitRegion.dstSubresource(), aspectMask);
		blitRegion.dstOffsets().get(0).set(0, 0, 0);
		blitRegion.dstOffsets().get(1).set(destWidth, destHeight, 1);

		vkCmdBlitImage(
				commandBuffer, vkSourceImage, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
				vkDestImage, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, imageBlitRegions, filter
		);
	}

	public void copyImageToBuffer(
			int aspectMask, long vkImage, int width, int height, long vkBuffer
	) {
		var bufferCopyRegions = VkBufferImageCopy.calloc(1, stack);
		var copyRegion = bufferCopyRegions.get(0);
		copyRegion.bufferOffset(0);
		copyRegion.bufferRowLength(width);
		copyRegion.bufferImageHeight(height);
		instance.images.subresourceLayers(copyRegion.imageSubresource(), aspectMask);
		copyRegion.imageOffset().set(0, 0, 0);
		copyRegion.imageExtent().set(width, height, 1);

		vkCmdCopyImageToBuffer(
				commandBuffer, vkImage, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, vkBuffer, bufferCopyRegions
		);
	}

	public void copyBufferToImage(
			int aspectMask, long vkImage, int width, int height, long vkBuffer
	) {
		var bufferCopyRegions = VkBufferImageCopy.calloc(1, stack);
		var copyRegion = bufferCopyRegions.get(0);
		copyRegion.bufferOffset(0);
		copyRegion.bufferRowLength(width);
		copyRegion.bufferImageHeight(height);
		instance.images.subresourceLayers(copyRegion.imageSubresource(), aspectMask);
		copyRegion.imageOffset().set(0, 0, 0);
		copyRegion.imageExtent().set(width, height, 1);

		vkCmdCopyBufferToImage(
				commandBuffer, vkBuffer, vkImage, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, bufferCopyRegions
		);
	}

	public void bufferBarrier(
			long vkBuffer, long offset, long size, ResourceUsage srcUsage, ResourceUsage dstUsage
	) {
		var bufferBarrier = VkBufferMemoryBarrier.calloc(1, stack);
		bufferBarrier.sType$Default();
		bufferBarrier.srcAccessMask(srcUsage.accessMask());
		bufferBarrier.dstAccessMask(dstUsage.accessMask());
		bufferBarrier.srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
		bufferBarrier.dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
		bufferBarrier.buffer(vkBuffer);
		bufferBarrier.offset(offset);
		bufferBarrier.size(size);

		vkCmdPipelineBarrier(
				commandBuffer, srcUsage.stageMask(), dstUsage.stageMask(),
				0, null, bufferBarrier, null
		);
	}

	public void clearColorImage(long vkImage, float red, float green, float blue, float alpha) {
		var pColor = VkClearColorValue.calloc(stack);
		pColor.float32(stack.floats(red, green, blue, alpha));

		var pRange = instance.images.subresourceRange(stack, null, VK_IMAGE_ASPECT_COLOR_BIT);
		vkCmdClearColorImage(commandBuffer, vkImage, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, pColor, pRange);
	}

	public void transitionColorLayout(
			long vkImage, ResourceUsage oldUsage, ResourceUsage newUsage
	) {
		transitionLayout(vkImage, oldUsage, newUsage, VK_IMAGE_ASPECT_COLOR_BIT);
	}

	public void transitionDepthLayout(
			long vkImage, ResourceUsage oldUsage, ResourceUsage newUsage
	) {
		transitionLayout(vkImage, oldUsage, newUsage, VK_IMAGE_ASPECT_DEPTH_BIT);
	}

	public void transitionLayout(
			long vkImage, ResourceUsage oldUsage, ResourceUsage newUsage, int aspectMask
	) {
		var pImageBarrier = VkImageMemoryBarrier.calloc(1, stack);
		pImageBarrier.sType$Default();
		pImageBarrier.srcAccessMask(oldUsage != null ? oldUsage.accessMask() : 0);
		pImageBarrier.dstAccessMask(newUsage.accessMask());
		pImageBarrier.oldLayout(oldUsage != null ? oldUsage.imageLayout() : VK_IMAGE_LAYOUT_UNDEFINED);
		pImageBarrier.newLayout(newUsage.imageLayout());
		pImageBarrier.srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
		pImageBarrier.dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
		pImageBarrier.image(vkImage);
		instance.images.subresourceRange(stack, pImageBarrier.subresourceRange(), aspectMask);

		vkCmdPipelineBarrier(
				commandBuffer, oldUsage != null ? oldUsage.stageMask() : VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
				newUsage.stageMask(), 0, null, null, pImageBarrier
		);
	}

	public void simpleColorRenderingAttachment(
			VkRenderingAttachmentInfo attachment, long imageView, int loadOp, int storeOp,
			float clearRed, float clearGreen, float clearBlue, float clearAlpha
	) {
		attachment.sType$Default();
		attachment.pNext(NULL);
		attachment.imageView(imageView);
		attachment.imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
		attachment.resolveMode(VK_RESOLVE_MODE_NONE);
		attachment.resolveImageView(VK_NULL_HANDLE);
		attachment.resolveImageLayout(VK_IMAGE_LAYOUT_UNDEFINED);
		attachment.loadOp(loadOp);
		attachment.storeOp(storeOp);

		var color = attachment.clearValue().color();
		color.float32(0, clearRed);
		color.float32(1, clearGreen);
		color.float32(2, clearBlue);
		color.float32(3, clearAlpha);
	}

	public VkRenderingAttachmentInfo simpleDepthRenderingAttachment(
			MemoryStack stack, long imageView, int imageLayout, int storeOp, Float depthClear, Integer stencilClear
	) {
		if ((depthClear == null) != (stencilClear == null)) {
			throw new IllegalArgumentException("depthClear must be null if and only if stencilClear is null");
		}

		var attachment = VkRenderingAttachmentInfo.calloc(stack);
		attachment.sType$Default();
		attachment.imageView(imageView);
		attachment.imageLayout(imageLayout);
		attachment.resolveMode(VK_RESOLVE_MODE_NONE);
		if (depthClear != null) attachment.loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR);
		else attachment.loadOp(VK_ATTACHMENT_LOAD_OP_LOAD);
		attachment.storeOp(storeOp);
		if (depthClear != null) attachment.clearValue().depthStencil().set(depthClear, stencilClear);
		else attachment.clearValue().depthStencil().set(0f, 0);

		return attachment;
	}

	public void beginSimpleDynamicRendering(
			int width, int height,
			VkRenderingAttachmentInfo.Buffer colorAttachments,
			VkRenderingAttachmentInfo depthAttachment,
			VkRenderingAttachmentInfo stencilAttachment
	) {
		var renderingInfo = VkRenderingInfo.calloc(stack);
		renderingInfo.sType$Default();
		renderingInfo.flags(0);
		renderingInfo.renderArea().offset().set(0, 0);
		renderingInfo.renderArea().extent().set(width, height);
		renderingInfo.layerCount(1);
		renderingInfo.viewMask(0);
		renderingInfo.pColorAttachments(colorAttachments);
		renderingInfo.pDepthAttachment(depthAttachment);
		renderingInfo.pStencilAttachment(stencilAttachment);

		if (instance.apiVersion >= VK_API_VERSION_1_3) vkCmdBeginRendering(commandBuffer, renderingInfo);
		else vkCmdBeginRenderingKHR(commandBuffer, renderingInfo);
	}

	public void endDynamicRendering() {
		if (instance.apiVersion >= VK_API_VERSION_1_3) vkCmdEndRendering(commandBuffer);
		else vkCmdEndRenderingKHR(commandBuffer);
	}

	public void dynamicViewportAndScissor(int width, int height) {
		var pViewport = VkViewport.calloc(1, stack);
		pViewport.x(0f);
		pViewport.y(0f);
		pViewport.width((float) width);
		pViewport.height((float) height);
		pViewport.minDepth(0f);
		pViewport.maxDepth(1f);

		var pScissor = VkRect2D.calloc(1, stack);
		pScissor.offset().set(0, 0);
		pScissor.extent().set(width, height);

		vkCmdSetViewport(commandBuffer, 0, pViewport);
		vkCmdSetScissor(commandBuffer, 0, pScissor);
	}

	public void end(String context) {
		assertVkSuccess(vkEndCommandBuffer(commandBuffer), "EndCommandBuffer", context);
	}

	public void end() {
		if (this.context == null) throw new IllegalStateException("Use end(context) instead");
		end(this.context);
	}
}
