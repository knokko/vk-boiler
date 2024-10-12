package com.github.knokko.boiler.commands;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.buffers.VkbBufferRange;
import com.github.knokko.boiler.images.VkbImage;
import com.github.knokko.boiler.synchronization.ResourceUsage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.vulkan.KHRDynamicRendering.vkCmdBeginRenderingKHR;
import static org.lwjgl.vulkan.KHRDynamicRendering.vkCmdEndRenderingKHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.VK_RESOLVE_MODE_NONE;
import static org.lwjgl.vulkan.VK13.*;

/**
 * This is a wrapper class for <i>VkCommandBuffer</i> that provides convenient methods to get rid of boilerplate code
 * for command buffer recording.
 */
public class CommandRecorder {

	/**
	 * Wraps the given command buffer, and calls <i>VkBeginCommandBuffer</i> on it.
	 * @param stack The <i>MemoryStack</i> that the recorder may use. It must be valid until you finish recording.
	 * @param context When <i>vkBeginCommandBuffer</i> or <i>vkEndCommandBuffer</i> fails, an exception will be thrown,
	 *                which will include <i>context</i> in its message.
	 * @return The wrapped command buffer
	 */
	public static CommandRecorder begin(
			VkCommandBuffer commandBuffer, BoilerInstance instance, MemoryStack stack, String context
	) {
		return begin(commandBuffer, instance, stack, 0, context);
	}

	/**
	 * Wraps the given command buffer, and calls <i>VkBeginCommandBuffer</i> on it.
	 * @param stack The <i>MemoryStack</i> that the recorder may use. It must be valid until you finish recording.
	 * @param flags The <i>VkCommandBufferUsageFlags</i> that will be passed to the <i>VkCommandBufferBeginInfo</i>
	 * @param context When <i>vkBeginCommandBuffer</i> or <i>vkEndCommandBuffer</i> fails, an exception will be thrown,
	 *                which will include <i>context</i> in its message.
	 * @return The wrapped command buffer
	 */
	public static CommandRecorder begin(
			VkCommandBuffer commandBuffer, BoilerInstance instance, MemoryStack stack, int flags, String context
	) {
		instance.commands.begin(commandBuffer, stack, flags, context);
		return new CommandRecorder(commandBuffer, instance, stack, context);
	}

	/**
	 * Wraps a command buffer that is already in the recording state
	 * @param stack The <i>MemoryStack</i> that the recorder may use. It must be valid until you finish recording.
	 * @return The wrapped command buffer
	 */
	public static CommandRecorder alreadyRecording(
			VkCommandBuffer commandBuffer, BoilerInstance instance, MemoryStack stack
	) {
		return new CommandRecorder(commandBuffer, instance, stack, null);
	}

	/**
	 * The <i>VkCommandBuffer</i> that was wrapped
	 */
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

	/**
	 * Calls <i>vkCmdCopyBuffer</i>
	 * @param source The source buffer
	 * @param vkDestBuffer The destination <i>VkBuffer</i>
	 * @param destOffset The byte offset into the destination buffer
	 */
	public void copyBuffer(VkbBufferRange source, long vkDestBuffer, long destOffset) {
		var copyRegion = VkBufferCopy.calloc(1, stack);
		copyRegion.srcOffset(source.offset());
		copyRegion.dstOffset(destOffset);
		copyRegion.size(source.size());

		vkCmdCopyBuffer(commandBuffer, source.buffer().vkBuffer(), vkDestBuffer, copyRegion);
	}

	/**
	 * Calls <i>vkCmdCopyImage</i>
	 * @param source The source image
	 * @param dest The destination image, must be at least as large as <i>source</i>
	 */
	public void copyImage(VkbImage source, VkbImage dest) {
		var imageCopyRegions = VkImageCopy.calloc(1, stack);
		var copyRegion = imageCopyRegions.get(0);
		instance.images.subresourceLayers(copyRegion.srcSubresource(), source.aspectMask());
		copyRegion.srcOffset().set(0, 0, 0);
		instance.images.subresourceLayers(copyRegion.dstSubresource(), dest.aspectMask());
		copyRegion.dstOffset().set(0, 0, 0);
		copyRegion.extent().set(source.width(), source.height(), 1);

		vkCmdCopyImage(
				commandBuffer, source.vkImage(), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
				dest.vkImage(), VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, imageCopyRegions
		);
	}

	/**
	 * Calls <i>vkCmdBlitImage</i>
	 * @param filter The <i>VkFilter</i> that should be passed to <i>vkCmdBlitImage</i>
	 * @param source The source image
	 * @param dest The destination image
	 */
	@SuppressWarnings("resource")
	public void blitImage(int filter, VkbImage source, VkbImage dest) {
		var imageBlitRegions = VkImageBlit.calloc(1, stack);
		var blitRegion = imageBlitRegions.get(0);
		instance.images.subresourceLayers(blitRegion.srcSubresource(), source.aspectMask());
		blitRegion.srcOffsets().get(0).set(0, 0, 0);
		blitRegion.srcOffsets().get(1).set(source.width(), source.height(), 1);
		instance.images.subresourceLayers(blitRegion.dstSubresource(), dest.aspectMask());
		blitRegion.dstOffsets().get(0).set(0, 0, 0);
		blitRegion.dstOffsets().get(1).set(dest.width(), dest.height(), 1);

		vkCmdBlitImage(
				commandBuffer, source.vkImage(), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
				dest.vkImage(), VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, imageBlitRegions, filter
		);
	}

	/**
	 * Calls <i>vkCmdCopyImageToBuffer</i>
	 * @param image The source image
	 * @param vkBuffer The destination buffer
	 */
	public void copyImageToBuffer(VkbImage image, long vkBuffer) {
		var bufferCopyRegions = VkBufferImageCopy.calloc(1, stack);
		var copyRegion = bufferCopyRegions.get(0);
		copyRegion.bufferOffset(0);
		copyRegion.bufferRowLength(image.width());
		copyRegion.bufferImageHeight(image.height());
		instance.images.subresourceLayers(copyRegion.imageSubresource(), image.aspectMask());
		copyRegion.imageOffset().set(0, 0, 0);
		copyRegion.imageExtent().set(image.width(), image.height(), 1);

		vkCmdCopyImageToBuffer(
				commandBuffer, image.vkImage(), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, vkBuffer, bufferCopyRegions
		);
	}

	/**
	 * Calls <i>vkCmdCopyBufferToImage</i>
	 * @param image The destination image
	 * @param vkBuffer The source buffer
	 */
	public void copyBufferToImage(VkbImage image, long vkBuffer) {
		var bufferCopyRegions = VkBufferImageCopy.calloc(1, stack);
		var copyRegion = bufferCopyRegions.get(0);
		copyRegion.bufferOffset(0);
		copyRegion.bufferRowLength(image.width());
		copyRegion.bufferImageHeight(image.height());
		instance.images.subresourceLayers(copyRegion.imageSubresource(), image.aspectMask());
		copyRegion.imageOffset().set(0, 0, 0);
		copyRegion.imageExtent().set(image.width(), image.height(), 1);

		vkCmdCopyBufferToImage(
				commandBuffer, vkBuffer, image.vkImage(), VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, bufferCopyRegions
		);
	}

	/**
	 * Uses <i>vkCmdPipelineBarrier</i> to record a buffer barrier.
	 * @param bufferRange The buffer range that should be covered by the barrier
	 * @param srcUsage The <i>srcAccessMask</i> and <i>srcStageMask</i>
	 * @param dstUsage The <i>dstAccessMask</i> and <i>dstStageMask</i>
	 */
	public void bufferBarrier(VkbBufferRange bufferRange, ResourceUsage srcUsage, ResourceUsage dstUsage) {
		var bufferBarrier = VkBufferMemoryBarrier.calloc(1, stack);
		bufferBarrier.sType$Default();
		bufferBarrier.srcAccessMask(srcUsage.accessMask());
		bufferBarrier.dstAccessMask(dstUsage.accessMask());
		bufferBarrier.srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
		bufferBarrier.dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
		bufferBarrier.buffer(bufferRange.buffer().vkBuffer());
		bufferBarrier.offset(bufferRange.offset());
		bufferBarrier.size(bufferRange.size());

		vkCmdPipelineBarrier(
				commandBuffer, srcUsage.stageMask(), dstUsage.stageMask(),
				0, null, bufferBarrier, null
		);
	}

	/**
	 * Calls <i>vkCmdClearColorImage</i>
	 * @param vkImage The <i>VkImage</i> to be cleared
	 * @param red The red component of the clear color, in range [0, 1]
	 * @param green The green component of the clear color, in range [0, 1]
	 * @param blue The blue component of the clear color, in range [0, 1]
	 * @param alpha The alpha component of the clear color, in range [0, 1]
	 */
	public void clearColorImage(long vkImage, float red, float green, float blue, float alpha) {
		var pColor = VkClearColorValue.calloc(stack);
		pColor.float32(stack.floats(red, green, blue, alpha));

		var pRange = instance.images.subresourceRange(stack, null, VK_IMAGE_ASPECT_COLOR_BIT);
		vkCmdClearColorImage(commandBuffer, vkImage, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, pColor, pRange);
	}

	/**
	 * Calls <i>vkCmdPipelineBarrier</i> to transition the layout of an image
	 * @param image The image whose layout should be transitioned
	 * @param oldUsage Contains the oldLayout, srcAccessMask, and srcStageMask. May be <i>null</i> to transition
	 *                 from <i>VK_IMAGE_LAYOUT_UNDEFINED</i>
	 * @param newUsage Contains the newLayout, dstAccessMask, and dstStageMask
	 */
	public void transitionLayout(
			VkbImage image, ResourceUsage oldUsage, ResourceUsage newUsage
	) {
		var pImageBarrier = VkImageMemoryBarrier.calloc(1, stack);
		pImageBarrier.sType$Default();
		pImageBarrier.srcAccessMask(oldUsage != null ? oldUsage.accessMask() : 0);
		pImageBarrier.dstAccessMask(newUsage.accessMask());
		pImageBarrier.oldLayout(oldUsage != null ? oldUsage.imageLayout() : VK_IMAGE_LAYOUT_UNDEFINED);
		pImageBarrier.newLayout(newUsage.imageLayout());
		pImageBarrier.srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
		pImageBarrier.dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
		pImageBarrier.image(image.vkImage());
		instance.images.subresourceRange(stack, pImageBarrier.subresourceRange(), image.aspectMask());

		vkCmdPipelineBarrier(
				commandBuffer, oldUsage != null ? oldUsage.stageMask() : VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
				newUsage.stageMask(), 0, null, null, pImageBarrier
		);
	}

	/**
	 * Populates the given <i>VkRenderingAttachmentInfo</i> for a color image. This is typically used right before
	 * <i>beginSimpleDynamicRendering</i>
	 */
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

	/**
	 * Populates the given <i>VkRenderingAttachmentInfo</i> for a depth/stencil image. This is typically used right
	 * before <i>beginSimpleDynamicRendering</i>
	 */
	public VkRenderingAttachmentInfo simpleDepthRenderingAttachment(
			long imageView, int imageLayout, int storeOp, Float depthClear, Integer stencilClear
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

	/**
	 * Calls <i>vkCmdBeginRendering(KHR)</i>. Note that you can use <i>simpleColorRenderingAttachment</i> and
	 * <i>simpleDepthRenderingAttachment</i> to populate the parameters.
	 * @param width The width of the attached images, in pixels
	 * @param height The height of the attached images, in pixels
	 * @param colorAttachments The color attachments, may be null
	 * @param depthAttachment The depth attachment, may be null
	 * @param stencilAttachment The stencil attachment, may be null
	 */
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

	private void bindDescriptors(int bindPoint, long pipelineLayout, long... descriptorSets) {
		vkCmdBindDescriptorSets(
				commandBuffer, bindPoint, pipelineLayout, 0, stack.longs(descriptorSets), null
		);
	}

	/**
	 * Calls <i>vkCmdBindDescriptorSets</i> using the given pipeline layout and descriptor sets. The <i>firstSet</i>
	 * will be 0 and <i>pDynamicOffsets</i> will be <b>null</b>. The <i>pipelineBindPoint</i> will be
	 * 	 * <i>VK_PIPELINE_BIND_POINT_GRAPHICS</i>.
	 */
	public void bindGraphicsDescriptors(long pipelineLayout, long... descriptorSets) {
		bindDescriptors(VK_PIPELINE_BIND_POINT_GRAPHICS, pipelineLayout, descriptorSets);
	}

	/**
	 * Calls <i>vkCmdBindDescriptorSets</i> using the given pipeline layout and descriptor sets. The <i>firstSet</i>
	 * will be 0 and <i>pDynamicOffsets</i> will be <b>null</b>. The <i>pipelineBindPoint</i> will be
	 * <i>VK_PIPELINE_BIND_POINT_COMPUTE</i>.
	 */
	public void bindComputeDescriptors(long pipelineLayout, long... descriptorSets) {
		bindDescriptors(VK_PIPELINE_BIND_POINT_COMPUTE, pipelineLayout, descriptorSets);
	}

	/**
	 * Calls <i>vkCmdEndRendering(KHR)</i>
	 */
	public void endDynamicRendering() {
		if (instance.apiVersion >= VK_API_VERSION_1_3) vkCmdEndRendering(commandBuffer);
		else vkCmdEndRenderingKHR(commandBuffer);
	}

	/**
	 * Calls <i>vkCmdSetViewport</i> and <i>vkCmdSetScissor</i>
	 * @param width The width of the viewport and scissor, in pixels
	 * @param height The height of the viewport and scissor, in pixels
	 */
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

	/**
	 * Calls <i>vkEndCommandBuffer</i>. Note that you can call the <i>end()</i> method without parameters when you
	 * created this recorder using <i>CommandRecorder.begin</i>.
	 * @param context When <i>vkEndCommandBuffer</i> fails, an exception will be thrown, which will include
	 *                   <i>context</i> in its message.
	 */
	public void end(String context) {
		assertVkSuccess(vkEndCommandBuffer(commandBuffer), "EndCommandBuffer", context);
	}

	/**
	 * Calls <i>vkEndCommandBuffer</i>. Note that you must use <i>end(String context)</i> if you created this recorder
	 * using <i>CommandRecorder.alreadyRecording</i>
	 */
	public void end() {
		if (this.context == null) throw new IllegalStateException("Use end(context) instead");
		end(this.context);
	}
}
