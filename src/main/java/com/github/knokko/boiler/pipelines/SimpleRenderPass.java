package com.github.knokko.boiler.pipelines;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.memory.callbacks.CallbackUserData;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkSubpassDescription;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/**
 * This class provides a convenient {@link #create} method to create simple render pass. See the corresponding
 * doc comments for more information.
 */
public class SimpleRenderPass {

	/**
	 * <p>
	 *     Creates a render pass with exactly 1 subpass and without any layout transitions or subpass dependencies.
	 * </p>
	 *
	 * <p>
	 *     Using such a render pass is basically a verbose way of using dynamic rendering. The only advantage compared
	 *     to dynamic rendering is that this render pass will work on old (integrated) GPU drivers that don't support
	 *     dynamic rendering.
	 * </p>
	 *
	 * @param instance The {@link BoilerInstance}
	 * @param name The debug name (which is ignored when validation is not enabled)
	 * @param depthAttachment The info for the depth-stencil attachment, or {@code null} to create a render pass
	 *                        without depth-stencil attachment
	 * @param colorAttachments The info for the color attachments. The render pass will have
	 *                        {@code colorAttachments.length} color attachments
	 * @return The handle of the created render pass
	 */
	public static long create(
			BoilerInstance instance, String name,
			DepthStencilAttachment depthAttachment, ColorAttachment... colorAttachments
	) {
		try (MemoryStack stack = stackPush()) {
			int totalAttachments = colorAttachments.length;
			if (depthAttachment != null) totalAttachments += 1;

			var attachments = VkAttachmentDescription.calloc(totalAttachments, stack);
			var colorReferences = VkAttachmentReference.calloc(colorAttachments.length, stack);
			for (int index = 0; index < colorAttachments.length; index++) {
				var description = attachments.get(index);
				var attachment = colorAttachments[index];
				description.flags(0);
				description.format(attachment.format);
				description.samples(attachment.sampleCount);
				description.loadOp(attachment.loadOp);
				description.storeOp(attachment.storeOp);
				description.stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE);
				description.stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE);
				description.initialLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
				description.finalLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

				var reference = colorReferences.get(index);
				reference.attachment(index);
				reference.layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
			}

			VkAttachmentReference depthReference = null;
			if (depthAttachment != null) {
				var description = attachments.get(colorAttachments.length);
				description.flags(0);
				description.format(depthAttachment.format);
				description.samples(depthAttachment.sampleCount);
				description.loadOp(depthAttachment.loadOp);
				description.storeOp(depthAttachment.storeOp);
				description.stencilLoadOp(depthAttachment.stencilLoadOp);
				description.stencilStoreOp(depthAttachment.stencilStoreOp);
				description.initialLayout(depthAttachment.layout);
				description.finalLayout(depthAttachment.layout);

				depthReference = VkAttachmentReference.calloc(stack);
				depthReference.attachment(colorAttachments.length);
				depthReference.layout(depthAttachment.layout);
			}

			var subpass = VkSubpassDescription.calloc(1, stack);
			subpass.flags(0);
			subpass.pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS);
			subpass.pInputAttachments(null);
			subpass.colorAttachmentCount(1);
			subpass.pColorAttachments(colorReferences);
			subpass.pResolveAttachments(null);
			subpass.pDepthStencilAttachment(depthReference);
			subpass.pPreserveAttachments(null);

			var ciPass = VkRenderPassCreateInfo.calloc(stack);
			ciPass.sType$Default();
			ciPass.pAttachments(attachments);
			ciPass.pSubpasses(subpass);
			ciPass.pDependencies(null);

			var pRenderPass = stack.callocLong(1);
			assertVkSuccess(vkCreateRenderPass(
					instance.vkDevice(), ciPass, CallbackUserData.RENDER_PASS.put(stack, instance), pRenderPass
			), "CreateRenderPass", name);
			long renderPass = pRenderPass.get(0);
			instance.debug.name(stack, renderPass, VK_OBJECT_TYPE_RENDER_PASS, name);
			return renderPass;
		}
	}

	/**
	 * Color attachment parameter type used in {@link #create}
	 *
	 * @param format The image format (e.g. VK_FORMAT_R8G8B8A8_SRGB)
	 * @param loadOp The load OP (e.g. VK_ATTACHMENT_LOAD_OP_CLEAR)
	 * @param storeOp The store OP (e.g. VK_ATTACHMENT_STORE_OP_STORE)
	 * @param sampleCount The sample count (e.g. VK_SAMPLE_COUNT_1_BIT)
	 */
	public record ColorAttachment(int format, int loadOp, int storeOp, int sampleCount) {}

	/**
	 * Depth/stencil attachment parameter type used in {@link #create}
	 *
	 * @param format The image format (e.g. VK_FORMAT_D32_SFLOAT)
	 * @param layout The image layout (e.g. VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
	 * @param loadOp The depth load OP (e.g. VK_ATTACHMENT_LOAD_OP_CLEAR)
	 * @param storeOp The depth store OP (e.g. VK_ATTACHMENT_STORE_OP_DONT_CARE)
	 * @param stencilLoadOp The stencil load OP
	 * @param stencilStoreOp The stencil store OP
	 * @param sampleCount The sample count (e.g. VK_SAMPLE_COUNT_1_BIT)
	 */
	public record DepthStencilAttachment(
			int format, int layout,
			int loadOp, int storeOp,
			int stencilLoadOp, int stencilStoreOp,
			int sampleCount
	) {

		/**
		 * Simplified constructor for depth attachments where the stencil component is unused, or not present at all.
		 * @param loadOp The depth load OP
		 * @param storeOp The depth store OP
		 */
		public static DepthStencilAttachment simpleDepthOnly(int format, int loadOp, int storeOp) {
			return new DepthStencilAttachment(
					format, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL, loadOp, storeOp,
					VK_ATTACHMENT_LOAD_OP_DONT_CARE, VK_ATTACHMENT_STORE_OP_DONT_CARE, VK_SAMPLE_COUNT_1_BIT
			);
		}

		/**
		 * Simplified 'constructor' that assumes {@code VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL} and
		 * {@code VK_SAMPLE_COUNT_1_BIT}
		 */
		public static DepthStencilAttachment simpleDepthStencil(
				int format, int depthLoadOp, int depthStoreOp, int stencilLoadOp, int stencilStoreOp
		) {
			return new DepthStencilAttachment(
					format, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL, depthLoadOp, depthStoreOp,
					stencilLoadOp, stencilStoreOp, VK_SAMPLE_COUNT_1_BIT
			);
		}
	}
}
