package com.github.knokko.boiler.images;

import com.github.knokko.boiler.BoilerInstance;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.*;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVmaSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.vma.Vma.VMA_MEMORY_USAGE_AUTO;
import static org.lwjgl.util.vma.Vma.vmaCreateImage;
import static org.lwjgl.vulkan.VK10.*;

public class BoilerImages {

	private final BoilerInstance instance;

	/**
	 * This constructor is meant for internal use only. You should use <i>boilerInstance.images</i> instead.
	 */
	public BoilerImages(BoilerInstance instance) {
		this.instance = instance;
	}

	/**
	 * Creates an image and binds its memory using VMA. A corresponding image view is also created.
	 * This method simply calls <i>create(...)</i> with some default values.
	 * @param width The width of the image, in pixels
	 * @param height The height of the image, in pixels
	 * @param format The format of the image (e.g. <i>VK_FORMAT_R8G8B8A8_SRGB</i>)
	 * @param usage The image usage flags (e.g. <i>VK_IMAGE_USAGE_COLOR_ATTACHMENT_OPTIMAL</i>)
	 * @param aspectMask The image view aspect mask (usually <i>VK_IMAGE_ASPECT_COLOR_BIT</i>)
	 * @param name The debug name of the image (when <i>VK_EXT_debug_utils</i> is enabled)
	 * @return The wrapped image
	 * @deprecated Use {@link ImageBuilder} instead
	 */
	@Deprecated
	public VkbImage createSimple(int width, int height, int format, int usage, int aspectMask, String name) {
		return create(
				width, height, format, usage, aspectMask,
				VK_SAMPLE_COUNT_1_BIT, 1, 1, true, name
		);
	}

	/**
	 * Creates a (raw) image with unbound memory and without image view
	 * @param width The width of the image, in pixels
	 * @param height The height of the image, in pixels
	 * @param format The format of the image (e.g. <i>VK_FORMAT_R8G8B8A8_SRGB</i>)
	 * @param usage The image usage flags (e.g. <i>VK_IMAGE_USAGE_COLOR_ATTACHMENT_OPTIMAL</i>)
	 * @param aspectMask The image view aspect mask (usually <i>VK_IMAGE_ASPECT_COLOR_BIT</i>).
	 *                   It will be passed to the <i>aspectMask</i> field of the resulting <i>VkbImage</i>,
	 *                   but it won't be used in the actual image creation.
	 * @param samples The sample count of the image (usually <i>VK_SAMPLE_COUNT_1_BIT</i>)
	 * @param mipLevels The number of mip levels of the image (usually 1)
	 * @param arrayLayers The number of array layers of the image (usually 1)
	 * @param name The debug name of the image (when <i>VK_EXT_debug_utils</i> is enabled)
	 * @return The wrapped image
	 * @deprecated Use {@link ImageBuilder} instead
	 */
	@Deprecated
	public VkbImage createRaw(
			int width, int height, int format, int usage,
			int samples, int mipLevels, int arrayLayers, int aspectMask, String name
	) {
		try (var stack = stackPush()) {
			var ciImage = VkImageCreateInfo.calloc(stack);
			ciImage.sType$Default();
			ciImage.imageType(VK_IMAGE_TYPE_2D);
			ciImage.format(format);
			ciImage.extent().set(width, height, 1);
			ciImage.mipLevels(mipLevels);
			ciImage.arrayLayers(arrayLayers);
			ciImage.samples(samples);
			ciImage.tiling(VK_IMAGE_TILING_OPTIMAL);
			ciImage.usage(usage);
			ciImage.sharingMode(VK_SHARING_MODE_EXCLUSIVE);
			ciImage.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);

			var pImage = stack.callocLong(1);
			assertVkSuccess(vkCreateImage(
					instance.vkDevice(), ciImage, null, pImage
			), "CreateImage", name);
			long image = pImage.get(0);
			instance.debug.name(stack, image, VK_OBJECT_TYPE_IMAGE, name);

			return new VkbImage(image, VK_NULL_HANDLE, VK_NULL_HANDLE, width, height, aspectMask);
		}
	}

	/**
	 * Creates an image using <i>vmaCreateImage</i>, and optionally an image view for the image.
	 * @param width The width of the image, in pixels
	 * @param height The height of the image, in pixels
	 * @param format The format of the image (e.g. <i>VK_FORMAT_R8G8B8A8_SRGB</i>)
	 * @param usage The image usage flags (e.g. <i>VK_IMAGE_USAGE_COLOR_ATTACHMENT_OPTIMAL</i>)
	 * @param aspectMask The image view aspect mask (usually <i>VK_IMAGE_ASPECT_COLOR_BIT</i>).
	 *                   It will be ignored when <i>createView</i> is false
	 * @param samples The sample count of the image (usually <i>VK_SAMPLE_COUNT_1_BIT</i>)
	 * @param mipLevels The number of mip levels of the image (usually 1)
	 * @param arrayLayers The number of array layers of the image (usually 1)
	 * @param createView Whether this method should also create a <i>VkImageView</i> for the image
	 * @param name The debug name of the image (when <i>VK_EXT_debug_utils</i> is enabled)
	 * @return The wrapped image
	 * @deprecated Use {@link ImageBuilder} instead
	 */
	@Deprecated
	public VkbImage create(
			int width, int height, int format, int usage, int aspectMask,
			int samples, int mipLevels, int arrayLayers, boolean createView, String name
	) {
		try (var stack = stackPush()) {
			var ciImage = VkImageCreateInfo.calloc(stack);
			ciImage.sType$Default();
			ciImage.imageType(VK_IMAGE_TYPE_2D);
			ciImage.format(format);
			ciImage.extent().set(width, height, 1);
			ciImage.mipLevels(mipLevels);
			ciImage.arrayLayers(arrayLayers);
			ciImage.samples(samples);
			ciImage.tiling(VK_IMAGE_TILING_OPTIMAL);
			ciImage.usage(usage);
			ciImage.sharingMode(VK_SHARING_MODE_EXCLUSIVE);
			ciImage.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);

			var ciAllocation = VmaAllocationCreateInfo.calloc(stack);
			ciAllocation.usage(VMA_MEMORY_USAGE_AUTO);

			var pImage = stack.callocLong(1);
			var pAllocation = stack.callocPointer(1);
			assertVmaSuccess(vmaCreateImage(
					instance.vmaAllocator(), ciImage, ciAllocation, pImage, pAllocation, null
			), "CreateImage", name);
			long image = pImage.get(0);
			long allocation = pAllocation.get(0);
			instance.debug.name(stack, image, VK_OBJECT_TYPE_IMAGE, name);

			long view = createView ? createView(image, format, aspectMask, mipLevels, arrayLayers, name) : 0L;
			return new VkbImage(image, view, allocation, width, height, aspectMask);
		}
	}

	/**
	 * Creates an image view for an image using <i>createView</i>, but assumes some default parameters.
	 * @param image The <i>VkImage</i> for which a <i>VkImageView</i> should be created
	 * @param format The format of the image view (usually the same as the format of the image)
	 * @param aspectMask The aspect mask of the image view (usually <i>VK_IMAGE_ASPECT_COLOR_BIT</i>)
	 * @param name The debug name of the image view (when <i>VK_EXT_debug_utils</i> is enabled)
	 * @return The created <i>VkImageView</i> handle
	 */
	public long createSimpleView(long image, int format, int aspectMask, String name) {
		return createView(image, format, aspectMask, 1, 1, name);
	}

	/**
	 * Creates an image view for an image using <i>vkCreateImageView</i>
	 * @param image The <i>VkImage</i> for which a <i>VkImageView</i> should be created
	 * @param format The format of the image view (usually the same as the format of the image)
	 * @param aspectMask The aspect mask of the image view (usually <i>VK_IMAGE_ASPECT_COLOR_BIT</i>)
	 * @param mipLevels The number of mip levels of the image view (usually the same as that of the image)
	 * @param arrayLayers The number of array layers of the image view (usually the same as that of the image)
	 * @param name The debug name of the image view (when <i>VK_EXT_debug_utils</i> is enabled)
	 * @return The created <i>VkImageView</i> handle
	 */
	public long createView(long image, int format, int aspectMask, int mipLevels, int arrayLayers, String name) {
		try (var stack = stackPush()) {
			var ciImageView = VkImageViewCreateInfo.calloc(stack);
			ciImageView.sType$Default();
			ciImageView.image(image);
			if (arrayLayers > 1) ciImageView.viewType(VK_IMAGE_VIEW_TYPE_2D_ARRAY);
			else ciImageView.viewType(VK_IMAGE_VIEW_TYPE_2D);
			ciImageView.format(format);
			ciImageView.components().set(
					VK_COMPONENT_SWIZZLE_IDENTITY, VK_COMPONENT_SWIZZLE_IDENTITY,
					VK_COMPONENT_SWIZZLE_IDENTITY, VK_COMPONENT_SWIZZLE_IDENTITY
			);
			instance.images.subresourceRange(stack, ciImageView.subresourceRange(), aspectMask);
			ciImageView.subresourceRange().levelCount(mipLevels);
			ciImageView.subresourceRange().layerCount(arrayLayers);

			var pImageView = stack.callocLong(1);
			assertVkSuccess(vkCreateImageView(
					instance.vkDevice(), ciImageView, null, pImageView
			), "CreateImageView", name);
			long imageView = pImageView.get(0);
			instance.debug.name(stack, imageView, VK_OBJECT_TYPE_IMAGE_VIEW, name);
			return imageView;
		}
	}

	/**
	 * Uses <i>vkCreateFramebuffer</i> to create a framebuffer with the given image views
	 * @param renderPass The <i>VkRenderPass</i>
	 * @param width The width of the image views, in pixels
	 * @param height The height of the image views, in pixels
	 * @param name The debug name of the framebuffer (when <i>VK_EXT_debug_utils</i> is enabled)
	 * @param imageViews The <i>VkImageView</i>s of the framebuffer
	 * @return The created <i>VkFramebuffer</i> handle
	 */
	public long createFramebuffer(long renderPass, int width, int height, String name, long... imageViews) {
		try (var stack = stackPush()) {
			var ciFramebuffer = VkFramebufferCreateInfo.calloc(stack);
			ciFramebuffer.sType$Default();
			ciFramebuffer.flags(0);
			ciFramebuffer.renderPass(renderPass);
			ciFramebuffer.attachmentCount(1);
			ciFramebuffer.pAttachments(stack.longs(imageViews));
			ciFramebuffer.width(width);
			ciFramebuffer.height(height);
			ciFramebuffer.layers(1);

			var pFramebuffer = stack.callocLong(1);
			assertVkSuccess(vkCreateFramebuffer(
					instance.vkDevice(), ciFramebuffer, null, pFramebuffer
			), "CreateFramebuffer", name);
			long framebuffer = pFramebuffer.get(0);
			instance.debug.name(stack, framebuffer, VK_OBJECT_TYPE_FRAMEBUFFER, name);
			return framebuffer;
		}
	}

	/**
	 * Uses <i>vkCreateSampler</i> to create a <i>VkSampler</i>, but assumes some default parameter values
	 * @param magMinFilter The <i>magFilter</i> and <i>minFilter</i> (e.g. <i>VK_FILTER_NEAREST</i>)
	 * @param mipMapMode The <i>VkSamplerMipmapMode</i> (e.g. <i>VK_SAMPLER_MIPMAP_MODE_NEAREST</i>)
	 * @param addressMode The <i>VkSamplerAddressMode</i> for the U, V, and W coordinates
	 *                       (e.g. <i>VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE</i>)
	 * @param name The debug name of the sampler (when <i>VK_EXT_debug_utils</i> is enabled)
	 * @return The created <i>VkSampler</i> handle
	 */
	public long createSimpleSampler(int magMinFilter, int mipMapMode, int addressMode, String name) {
		return createSampler(magMinFilter, mipMapMode, addressMode, 0f, 0f, true, name);
	}

	/**
	 * Uses <i>vkCreateSampler</i> to create a <i>VkSampler</i>
	 * @param magMinFilter The <i>magFilter</i> and <i>minFilter</i> (e.g. <i>VK_FILTER_NEAREST</i>)
	 * @param mipMapMode The <i>VkSamplerMipmapMode</i> (e.g. <i>VK_SAMPLER_MIPMAP_MODE_NEAREST</i>)
	 * @param addressMode The <i>VkSamplerAddressMode</i> for the U, V, and W coordinates
	 *                       (e.g. <i>VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE</i>)
	 * @param minLod <i>VkSamplerCreateInfo.minLod</i>
	 * @param maxLod <i>VkSamplerCreateInfo.maxLod</i>
	 * @param normalized Whether normalized texture coordinates are used
	 * @param name The debug name of the sampler (when <i>VK_EXT_debug_utils</i> is enabled)
	 * @return The created <i>VkSampler</i> handle
	 */
	public long createSampler(
			int magMinFilter, int mipMapMode, int addressMode,
			float minLod, float maxLod, boolean normalized, String name
	) {
		try (var stack = stackPush()) {
			var ciSampler = VkSamplerCreateInfo.calloc(stack);
			ciSampler.sType$Default();
			ciSampler.magFilter(magMinFilter);
			ciSampler.minFilter(magMinFilter);
			ciSampler.mipmapMode(mipMapMode);
			ciSampler.addressModeU(addressMode);
			ciSampler.addressModeV(addressMode);
			ciSampler.addressModeW(addressMode);
			ciSampler.mipLodBias(0f);
			ciSampler.anisotropyEnable(false);
			ciSampler.compareEnable(false);
			ciSampler.compareOp(VK_COMPARE_OP_ALWAYS);
			ciSampler.minLod(minLod);
			ciSampler.maxLod(maxLod);
			ciSampler.borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK);
			ciSampler.unnormalizedCoordinates(!normalized);

			var pSampler = stack.callocLong(1);
			assertVkSuccess(vkCreateSampler(
							instance.vkDevice(), ciSampler, null, pSampler),
					"CreateSampler", name
			);
			long sampler = pSampler.get(0);
			instance.debug.name(stack, sampler, VK_OBJECT_TYPE_SAMPLER, name);
			return sampler;
		}
	}

	/**
	 * Populates the given <i>VkImageSubresourceRange</i> struct with the given aspect mask, a base mip level/array
	 * layer of 0, and a level/layer count of 1. When <i>range</i> is null, a new struct will be allocated on the given
	 * stack, and returned.
	 * @param stack The memory stack onto which the struct should be allocated when <i>range</i> is null
	 * @param range The <i>VkImageSubresourceRange</i> to be populated, or null to allocate a new one on the given stack
	 * @param aspectMask The aspect mask of the image
	 * @return the populated <i>VkImageSubresourceRange</i>. When <i>range</i> is not null, this method simply returns
	 * <i>range</i>.
	 */
	public VkImageSubresourceRange subresourceRange(MemoryStack stack, VkImageSubresourceRange range, int aspectMask) {
		if (range == null) range = VkImageSubresourceRange.calloc(stack);
		range.aspectMask(aspectMask);
		range.baseMipLevel(0);
		range.levelCount(1);
		range.baseArrayLayer(0);
		range.layerCount(1);
		return range;
	}

	/**
	 * Populates the given <i>VkImageSubresourceLayers</i> with the given aspect mask, a base mip level/array layer
	 * of 0, and a layer count of 1.
	 * @param layers The <i>VkImageSubresourceLayers</i> struct to populate
	 * @param aspectMask The image aspect mask
	 */
	public void subresourceLayers(VkImageSubresourceLayers layers, int aspectMask) {
		layers.aspectMask(aspectMask);
		layers.mipLevel(0);
		layers.baseArrayLayer(0);
		layers.layerCount(1);
	}

	/**
	 * Iterates over the given <i>VkFormat</i>s, and returns the first format such that the corresponding
	 * <i>optimalTilingFeatures</i> (as queried by <i>vkGetPhysicalDeviceFormatProperties</i>) has the
	 * <i>VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT</i> set.
	 * @param preferredFormats The <i>VkFormat</i>s that you are willing to use, such that the formats you prefer appear
	 *                         earlier (lower index) in the array
	 * @return The first format in <i>preferredFormats</i> that can be used as depth-stencil attachment
	 * @throws IllegalArgumentException When the graphics driver doesn't allow any of the <i>preferredFormats</i> to be
	 * used as depth-stencil attachment.
	 */
	public int chooseDepthStencilFormat(int... preferredFormats) {
		try (var stack = stackPush()) {
			var formatProps = VkFormatProperties.calloc(stack);
			for (int candidateFormat : preferredFormats) {
				vkGetPhysicalDeviceFormatProperties(instance.vkPhysicalDevice(), candidateFormat, formatProps);

				if ((formatProps.optimalTilingFeatures() & VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT) != 0) {
					return candidateFormat;
				}
			}
		}

		throw new IllegalArgumentException("None of the preferred formats supports depth-stencil operations");
	}
}
