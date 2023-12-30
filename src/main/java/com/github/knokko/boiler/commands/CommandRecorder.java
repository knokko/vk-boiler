package com.github.knokko.boiler.commands;

import com.github.knokko.boiler.instance.BoilerInstance;
import com.github.knokko.boiler.sync.ResourceUsage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.vulkan.VK10.*;

public class CommandRecorder {

    public static CommandRecorder begin(
            VkCommandBuffer commandBuffer, BoilerInstance boiler, MemoryStack stack, String context
    ) {
        return begin(commandBuffer, boiler, stack, 0, context);
    }

    public static CommandRecorder begin(
            VkCommandBuffer commandBuffer, BoilerInstance boiler, MemoryStack stack, int flags, String context
    ) {
        boiler.commands.begin(commandBuffer, stack, flags, context);
        return new CommandRecorder(commandBuffer, boiler, stack, context);
    }

    public static CommandRecorder alreadyRecording(
            VkCommandBuffer commandBuffer, BoilerInstance boiler, MemoryStack stack
    ) {
        return new CommandRecorder(commandBuffer, boiler, stack, null);
    }

    private final VkCommandBuffer commandBuffer;
    private final BoilerInstance boiler;
    private final MemoryStack stack;
    private final String context;

    private CommandRecorder(VkCommandBuffer commandBuffer, BoilerInstance boiler, MemoryStack stack, String context) {
        this.commandBuffer = commandBuffer;
        this.boiler = boiler;
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
        boiler.images.subresourceLayers(stack, copyRegion.srcSubresource(), aspectMask);
        copyRegion.srcOffset().set(0, 0, 0);
        boiler.images.subresourceLayers(stack, copyRegion.dstSubresource(), aspectMask);
        copyRegion.dstOffset().set(0, 0, 0);
        copyRegion.extent().set(width, height, 1);

        vkCmdCopyImage(
                commandBuffer, vkSourceImage, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                vkDestImage, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, imageCopyRegions
        );
    }

    public void blitImage(
            int aspectMask, int filter, long vkSourceImage, int sourceWidth, int sourceHeight,
            long vkDestImage, int destWidth, int destHeight
    ) {
        var imageBlitRegions = VkImageBlit.calloc(1, stack);
        var blitRegion = imageBlitRegions.get(0);
        boiler.images.subresourceLayers(stack, blitRegion.srcSubresource(), aspectMask);
        blitRegion.srcOffsets().get(0).set(0, 0, 0);
        blitRegion.srcOffsets().get(1).set(sourceWidth, sourceHeight, 1);
        boiler.images.subresourceLayers(stack, blitRegion.dstSubresource(), aspectMask);
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
        boiler.images.subresourceLayers(stack, copyRegion.imageSubresource(), aspectMask);
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
        boiler.images.subresourceLayers(stack, copyRegion.imageSubresource(), aspectMask);
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

    public void transitionColorLayout(
            long vkImage, int oldLayout, int newLayout, ResourceUsage oldUsage, ResourceUsage newUsage
    ) {
        transitionLayout(vkImage, oldLayout, newLayout, oldUsage, newUsage, VK_IMAGE_ASPECT_COLOR_BIT);
    }

    public void transitionDepthLayout(
            long vkImage, int oldLayout, int newLayout,
            ResourceUsage oldUsage, ResourceUsage newUsage
    ) {
        transitionLayout(vkImage, oldLayout, newLayout, oldUsage, newUsage, VK_IMAGE_ASPECT_DEPTH_BIT);
    }

    public void transitionLayout(
            long vkImage, int oldLayout, int newLayout,
            ResourceUsage oldUsage, ResourceUsage newUsage, int aspectMask
    ) {
        var pImageBarrier = VkImageMemoryBarrier.calloc(1, stack);
        pImageBarrier.sType$Default();
        pImageBarrier.srcAccessMask(oldUsage != null ? oldUsage.accessMask() : 0);
        pImageBarrier.dstAccessMask(newUsage != null ? newUsage.accessMask() : 0);
        pImageBarrier.oldLayout(oldLayout);
        pImageBarrier.newLayout(newLayout);
        pImageBarrier.srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
        pImageBarrier.dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
        pImageBarrier.image(vkImage);
        boiler.images.subresourceRange(stack, pImageBarrier.subresourceRange(), aspectMask);

        vkCmdPipelineBarrier(
                commandBuffer, oldUsage != null ? oldUsage.stageMask() : VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                newUsage != null ? newUsage.stageMask() : VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
                0, null, null, pImageBarrier
        );
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
