package com.github.knokko.boiler.commands;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.buffers.VkbBuffer;
import com.github.knokko.boiler.images.VkbImage;
import com.github.knokko.boiler.synchronization.ResourceUsage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static com.github.knokko.boiler.utilities.ColorPacker.*;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.vulkan.KHRDynamicRendering.vkCmdBeginRenderingKHR;
import static org.lwjgl.vulkan.KHRDynamicRendering.vkCmdEndRenderingKHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.VK_RESOLVE_MODE_NONE;
import static org.lwjgl.vulkan.VK13.*;

/**
 * This is a wrapper class for <i>VkCommandBuffer</i> that provides convenient methods to get rid of boilerplate code
 * for command buffer recording.
 */
@SuppressWarnings("resource")
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

	/**
	 * The <i>MemoryStack</i> that the recorder may use. It is guaranteed to be valid until recording is finished.
	 */
	public final MemoryStack stack;
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
	 * @param destination The destination buffer
	 */
	public void copyBuffer(VkbBuffer source, VkbBuffer destination) {
		if (source.size != destination.size) throw new IllegalArgumentException(
				"Source size is " + source.size + ", but destination size is " + destination.size
		);
		var copyStruct = VkBufferCopy.calloc(1, stack);
		copyStruct.srcOffset(source.offset);
		copyStruct.dstOffset(destination.offset);
		copyStruct.size(source.size);

		vkCmdCopyBuffer(commandBuffer, source.vkBuffer, destination.vkBuffer, copyStruct);
	}

	/**
	 * (Repeatedly) calls <b>vkCmdCopyBuffer</b> to copy {@code sources[i]} to {@code destinations[i]} for each
	 * index {@code i} in {@code sources}. Up to 100 copies will be performed per call to <b>vkCmdCopyBuffer</b>.
	 *
	 * @param sources The source buffer (segments)
	 * @param destinations The destination buffer (segments), must have the same length as {@code sources}
	 */
	public void bulkCopyBuffers(VkbBuffer[] sources, VkbBuffer[] destinations) {
		if (sources.length != destinations.length) throw new IllegalArgumentException(
				"#sources (" + sources.length + ") must equal #destinations (" + destinations.length + ")"
		);
		if (sources.length == 0) return;

		boolean useHeap = sources.length > 10;
		int capacity = Math.min(sources.length, 100);

		var copyStructs = useHeap ? VkBufferCopy.calloc(capacity) : VkBufferCopy.calloc(capacity, stack);
		int structIndex = 0;
		long sourceVkBuffer = sources[0].vkBuffer;
		long destinationVkBuffer = destinations[0].vkBuffer;
		for (int sourceIndex = 0; sourceIndex < sources.length; sourceIndex++) {
			var source = sources[sourceIndex];
			var destination = destinations[sourceIndex];

			if (structIndex == capacity || source.vkBuffer != sourceVkBuffer || destination.vkBuffer != destinationVkBuffer) {
				copyStructs.limit(structIndex);
				vkCmdCopyBuffer(commandBuffer, sourceVkBuffer, destinationVkBuffer, copyStructs);
				structIndex = 0;
				sourceVkBuffer = source.vkBuffer;
				destinationVkBuffer = destination.vkBuffer;
			}

			var copyStruct = copyStructs.get(structIndex);
			copyStruct.srcOffset(source.offset);
			copyStruct.dstOffset(destination.offset);
			copyStruct.size(source.size);

			structIndex += 1;
		}

		copyStructs.limit(structIndex);
		vkCmdCopyBuffer(commandBuffer, sourceVkBuffer, destinationVkBuffer, copyStructs);

		if (useHeap) copyStructs.free();
	}

	/**
	 * Calls <i>vkCmdCopyImage</i>
	 * @param source The source image
	 * @param destination The destination image
	 */
	public void copyImage(VkbImage source, VkbImage destination) {
		var copyStruct = VkImageCopy.calloc(1, stack);
		instance.images.subresourceLayers(copyStruct.srcSubresource(), source.aspectMask);
		copyStruct.srcOffset().set(0, 0, 0);
		instance.images.subresourceLayers(copyStruct.dstSubresource(), destination.aspectMask);
		copyStruct.dstOffset().set(0, 0, 0);
		copyStruct.extent().set(source.width, source.height, 1);

		vkCmdCopyImage(
				commandBuffer, source.vkImage, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
				destination.vkImage, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, copyStruct
		);
	}

	/**
	 * Repeatedly calls <b>vkCmdCopyImage</b> to copy {@code sources[i]} to {@code destinations[i]} for each index
	 * {@code i}
	 * @param sources The source images
	 * @param destinations The destination images
	 */
	public void bulkCopyImages(VkbImage[] sources, VkbImage[] destinations) {
		var copyStruct = VkImageCopy.calloc(1, stack);
		copyStruct.srcOffset().set(0, 0, 0);
		copyStruct.dstOffset().set(0, 0, 0);

		for (int index = 0; index < sources.length; index++) {
			var source = sources[index];
			instance.images.subresourceLayers(copyStruct.srcSubresource(), source.aspectMask);
			instance.images.subresourceLayers(copyStruct.dstSubresource(), destinations[index].aspectMask);
			copyStruct.extent().set(source.width, source.height, 1);
			vkCmdCopyImage(
					commandBuffer, source.vkImage, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
					destinations[index].vkImage, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, copyStruct
			);
		}
	}

	/**
	 * Calls <i>vkCmdBlitImage</i>
	 * @param filter The <i>VkFilter</i> that should be passed to <i>vkCmdBlitImage</i>
	 * @param source The source image
	 * @param destination The destination image
	 */
	public void blitImage(int filter, VkbImage source, VkbImage destination) {
		var imageBlitRegions = VkImageBlit.calloc(1, stack);
		var blitRegion = imageBlitRegions.get(0);
		instance.images.subresourceLayers(blitRegion.srcSubresource(), source.aspectMask);
		blitRegion.srcOffsets().get(0).set(0, 0, 0);
		blitRegion.srcOffsets().get(1).set(source.width, source.height, 1);
		instance.images.subresourceLayers(blitRegion.dstSubresource(), destination.aspectMask);
		blitRegion.dstOffsets().get(0).set(0, 0, 0);
		blitRegion.dstOffsets().get(1).set(destination.width, destination.height, 1);

		vkCmdBlitImage(
				commandBuffer, source.vkImage, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
				destination.vkImage, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, imageBlitRegions, filter
		);
	}

	public void bulkBlitImages(int filter, VkbImage[] sources, VkbImage[] destinations) {
		var imageBlitRegions = VkImageBlit.calloc(1, stack);
		var blitRegion = imageBlitRegions.get(0);
		blitRegion.srcOffsets().get(0).set(0, 0, 0);
		blitRegion.dstOffsets().get(0).set(0, 0, 0);

		for (int index = 0; index < sources.length; index++) {
			var source = sources[index];
			instance.images.subresourceLayers(blitRegion.srcSubresource(), source.aspectMask);
			blitRegion.srcOffsets().get(1).set(source.width, source.height, 1);

			var destination = destinations[index];
			instance.images.subresourceLayers(blitRegion.dstSubresource(), destination.aspectMask);
			blitRegion.dstOffsets().get(1).set(destination.width, destination.height, 1);

			vkCmdBlitImage(
					commandBuffer, source.vkImage, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
					destination.vkImage, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, imageBlitRegions, filter
			);
		}
	}

	/**
	 * Calls <i>vkCmdCopyImageToBuffer</i>
	 * @param image The source image
	 * @param buffer The destination buffer
	 */
	public void copyImageToBuffer(VkbImage image, VkbBuffer buffer) {
		var bufferCopyRegions = VkBufferImageCopy.calloc(1, stack);
		var copyRegion = bufferCopyRegions.get(0);
		copyRegion.bufferOffset(buffer.offset);
		copyRegion.bufferRowLength(0);
		copyRegion.bufferImageHeight(0);
		instance.images.subresourceLayers(copyRegion.imageSubresource(), image.aspectMask);
		copyRegion.imageOffset().set(0, 0, 0);
		copyRegion.imageExtent().set(image.width, image.height, 1);

		vkCmdCopyImageToBuffer(
				commandBuffer, image.vkImage, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, buffer.vkBuffer, bufferCopyRegions
		);
	}

	/**
	 * (Repeatedly) calls <i>vkCmdCopyImageToBuffer</i> to copy each image in {@code images} to the corresponding buffer
	 * in {@code buffers} with the same index.
	 * @param images The source images
	 * @param buffers The destination buffers
	 */
	public void bulkCopyImageToBuffers(VkbImage[] images, VkbBuffer[] buffers) {
		var bufferCopyRegions = VkBufferImageCopy.calloc(1, stack);
		var copyRegion = bufferCopyRegions.get(0);
		copyRegion.bufferRowLength(0);
		copyRegion.bufferImageHeight(0);
		copyRegion.imageOffset().set(0, 0, 0);

		for (int index = 0; index < images.length; index++) {
			copyRegion.bufferOffset(buffers[index].offset);
			instance.images.subresourceLayers(copyRegion.imageSubresource(), images[index].aspectMask);
			copyRegion.imageExtent().set(images[index].width, images[index].height, 1);

			vkCmdCopyImageToBuffer(
					commandBuffer, images[index].vkImage, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
					buffers[index].vkBuffer, bufferCopyRegions
			);
		}
	}

	/**
	 * Calls <i>vkCmdCopyBufferToImage</i>
	 * @param image The destination image
	 * @param buffer The source buffer (segment)
	 */
	public void copyBufferToImage(VkbImage image, VkbBuffer buffer) {
		var bufferCopyRegions = VkBufferImageCopy.calloc(1, stack);
		var copyRegion = bufferCopyRegions.get(0);
		copyRegion.bufferOffset(buffer.offset);
		copyRegion.bufferRowLength(0);
		copyRegion.bufferImageHeight(0);
		instance.images.subresourceLayers(copyRegion.imageSubresource(), image.aspectMask);
		copyRegion.imageOffset().set(0, 0, 0);
		copyRegion.imageExtent().set(image.width, image.height, 1);

		vkCmdCopyBufferToImage(
				commandBuffer, buffer.vkBuffer, image.vkImage,
				VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, bufferCopyRegions
		);
	}

	/**
	 * Repeatedly calls <i>vkCmdCopyBufferToImage</i> to copy each buffer in {@code buffers} to the corresponding image
	 * in {@code images} with the same index.
	 * @param images The destination images
	 * @param buffers The source buffers
	 */
	public void bulkCopyBufferToImage(VkbImage[] images, VkbBuffer[] buffers) {
		if (images.length != buffers.length) throw new IllegalArgumentException("#images must be equal to #buffers");

		var bufferCopyRegions = VkBufferImageCopy.calloc(1, stack);
		var copyRegion = bufferCopyRegions.get(0);
		copyRegion.bufferRowLength(0);
		copyRegion.bufferImageHeight(0);
		copyRegion.imageOffset().set(0, 0, 0);

		for (int index = 0; index < images.length; index++) {
			copyRegion.bufferOffset(buffers[index].offset);
			instance.images.subresourceLayers(copyRegion.imageSubresource(), images[index].aspectMask);
			copyRegion.imageExtent().set(images[index].width, images[index].height, 1);

			vkCmdCopyBufferToImage(
					commandBuffer, buffers[index].vkBuffer, images[index].vkImage,
					VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, bufferCopyRegions
			);
		}
	}

	/**
	 * Uses <i>vkCmdPipelineBarrier</i> to record a buffer barrier.
	 * @param buffer The buffer (segment) that should be covered by the barrier
	 * @param srcUsage The <i>srcAccessMask</i> and <i>srcStageMask</i>
	 * @param dstUsage The <i>dstAccessMask</i> and <i>dstStageMask</i>
	 */
	public void bufferBarrier(VkbBuffer buffer, ResourceUsage srcUsage, ResourceUsage dstUsage) {
		var bufferBarrier = VkBufferMemoryBarrier.calloc(1, stack);
		bufferBarrier.sType$Default();
		bufferBarrier.srcAccessMask(srcUsage.accessMask());
		bufferBarrier.dstAccessMask(dstUsage.accessMask());
		bufferBarrier.srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
		bufferBarrier.dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
		bufferBarrier.buffer(buffer.vkBuffer);
		bufferBarrier.offset(buffer.offset);
		bufferBarrier.size(buffer.size);

		vkCmdPipelineBarrier(
				commandBuffer, srcUsage.stageMask(), dstUsage.stageMask(),
				0, null, bufferBarrier, null
		);
	}

	/**
	 * (Repeatedly) calls <b>vkCmdPipelineBarrier</b> to record a buffer barrier for each buffer in {@code buffers}.
	 * Up to 100 buffers will be targeted per call to <b>vkCmdPipelineBarrier</b>.
	 * @param srcUsage The <i>srcAccessMask</i> and <i>srcStageMask</i>
	 * @param dstUsage The <i>dstAccessMask</i> and <i>dstStageMask</i>
	 * @param buffers The buffer (segments) for which a pipeline barrier should be recorded
	 */
	public void bulkBufferBarrier(ResourceUsage srcUsage, ResourceUsage dstUsage, VkbBuffer... buffers) {
		boolean useHeap = buffers.length > 10;
		int capacity = Math.min(buffers.length, 100);
		var pBufferBarriers = useHeap ? VkBufferMemoryBarrier.calloc(capacity) : VkBufferMemoryBarrier.calloc(capacity, stack);

		for (int index = 0; index < capacity; index++) {
			var bufferBarrier = pBufferBarriers.get(index);
			bufferBarrier.sType$Default();
			bufferBarrier.srcAccessMask(srcUsage.accessMask());
			bufferBarrier.dstAccessMask(dstUsage.accessMask());
			bufferBarrier.srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
			bufferBarrier.dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
		}

		int index = 0;
		int total = 0;
		for (VkbBuffer buffer : buffers) {
			var bufferBarrier = pBufferBarriers.get(index);
			bufferBarrier.buffer(buffer.vkBuffer);
			bufferBarrier.offset(buffer.offset);
			bufferBarrier.size(buffer.size);

			index += 1;
			total += 1;
			if (index == capacity || total == buffers.length) {
				pBufferBarriers.limit(index);
				vkCmdPipelineBarrier(
						commandBuffer, srcUsage.stageMask(), dstUsage.stageMask(),
						0, null, pBufferBarriers, null
				);
				index = 0;
			}
		}

		if (useHeap) memFree(pBufferBarriers);
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
		pImageBarrier.image(image.vkImage);
		instance.images.subresourceRange(stack, pImageBarrier.subresourceRange(), image.aspectMask);

		vkCmdPipelineBarrier(
				commandBuffer, oldUsage != null ? oldUsage.stageMask() : VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
				newUsage.stageMask(), 0, null, null, pImageBarrier
		);
	}

	/**
	 * Repeatedly calls <i>vkCmdPipelineBarrier</i> to transition the layout of each image in {@code images}.
	 * @param oldUsage Contains the oldLayout, srcAccessMask, and srcStageMask. May be <i>null</i> to transition
	 *                 from <i>VK_IMAGE_LAYOUT_UNDEFINED</i>
	 * @param newUsage Contains the newLayout, dstAccessMask, and dstStageMask
	 * @param images The images whose layout should be transitioned
	 */
	public void bulkTransitionLayout(ResourceUsage oldUsage, ResourceUsage newUsage, VkbImage... images) {
		boolean useHeap = images.length > 10;
		int capacity = Math.min(images.length, 100);
		var pImageBarriers = useHeap ? VkImageMemoryBarrier.calloc(capacity) : VkImageMemoryBarrier.calloc(capacity, stack);

		for (int index = 0; index < capacity; index++) {
			var pImageBarrier = pImageBarriers.get(index);
			pImageBarrier.sType$Default();
			pImageBarrier.srcAccessMask(oldUsage != null ? oldUsage.accessMask() : 0);
			pImageBarrier.dstAccessMask(newUsage.accessMask());
			pImageBarrier.oldLayout(oldUsage != null ? oldUsage.imageLayout() : VK_IMAGE_LAYOUT_UNDEFINED);
			pImageBarrier.newLayout(newUsage.imageLayout());
			pImageBarrier.srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
			pImageBarrier.dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
		}

		int index = 0;
		int total = 0;
		for (VkbImage image : images) {
			var pImageBarrier = pImageBarriers.get(index);
			pImageBarrier.image(image.vkImage);
			instance.images.subresourceRange(stack, pImageBarrier.subresourceRange(), image.aspectMask);

			index += 1;
			total += 1;
			if (index == capacity || total == images.length) {
				pImageBarriers.limit(index);
				vkCmdPipelineBarrier(
						commandBuffer, oldUsage != null ? oldUsage.stageMask() : VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
						newUsage.stageMask(), 0, null, null, pImageBarriers
				);
				index = 0;
			}
		}

		if (useHeap) memFree(pImageBarriers);
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
	 * Creates and returns a {@link VkRenderingAttachmentInfo.Buffer} with a capacity of 1, and populates it using
	 * {@link #simpleColorRenderingAttachment}
	 */
	public VkRenderingAttachmentInfo.Buffer singleColorRenderingAttachment(
			long imageView, int loadOp, int storeOp, int clearColor
	) {
		var attachments = VkRenderingAttachmentInfo.calloc(1, stack);
		simpleColorRenderingAttachment(
				attachments.get(0), imageView, loadOp, storeOp, normalize(red(clearColor)),
				normalize(green(clearColor)), normalize(blue(clearColor)), normalize(alpha(clearColor))
		);
		return attachments;
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
	 * Calls <i>vkCmdBeginRendering(KHR)</i>. Note that you can use {@link #simpleColorRenderingAttachment}
	 * {@link #singleColorRenderingAttachment}, and {@link #simpleDepthRenderingAttachment} to populate the parameters.
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
	 * Calls <i>vkCmdBindVertexBuffers</i> using the given {@code firstBinding} such that {@code pBuffers} and
	 * {@code pOffsets} get the vertex buffers and offsets of {@code vertexBuffers}
	 * @param firstBinding Will be propagated to {@link VK10#vkCmdBindVertexBuffers}
	 * @param vertexBuffers The vertex buffer (segments) that should be propagated to {@code pBuffers} and {@code pOffsets}
	 */
	public void bindVertexBuffers(int firstBinding, VkbBuffer... vertexBuffers) {
		var pBuffers = stack.callocLong(vertexBuffers.length);
		var pOffsets = stack.callocLong(vertexBuffers.length);
		for (int index = 0; index < vertexBuffers.length; index++) {
			pBuffers.put(index, vertexBuffers[index].vkBuffer);
			pOffsets.put(index, vertexBuffers[index].offset);
		}
		vkCmdBindVertexBuffers(commandBuffer, firstBinding, pBuffers, pOffsets);
	}

	/**
	 * Uses <i>vkCmdBindIndexBuffer</i> to bind the given index buffer range, with the given {@code indexType}
	 * @param indexBuffer The index buffer (segment) to be bound
	 * @param indexType Will be propagated to {@link VK10#vkCmdBindIndexBuffer}
	 */
	public void bindIndexBuffer(VkbBuffer indexBuffer, int indexType) {
		vkCmdBindIndexBuffer(commandBuffer, indexBuffer.vkBuffer, indexBuffer.offset, indexType);
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
