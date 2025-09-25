package com.github.knokko.boiler.images;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.memory.MemoryTypeSelector;
import com.github.knokko.boiler.memory.callbacks.CallbackUserData;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageViewCreateInfo;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/**
 * The ImageBuilder class can be used to configure the parameters for the creation of a {@link VkbImage}.
 * The basic flow is:
 * <ol>
 *     <li>Create an ImageBuilder instance using the constructor (name, width, height)</li>
 *     <li>
 *         Chain a 'basic usage' method, like {@link #texture} or {@link #depthAttachment}
 *         (optional, but recommended)
 *     </li>
 *     <li>
 *         Chain a setter method to override individual properties
 *         (for instance {@link #format} if you want a {@link #texture}, but not VK_FORMAT_R8G8B8A8_SRGB).
 *         This step is also optional, and often not needed.
 *     </li>
 * </ol>
 * After following these steps, it's time to actually create the image. The recommended approach is to use the
 * {@link com.github.knokko.boiler.memory.MemoryCombiner#addImage(ImageBuilder, float)} method.
 * Alternatively, you can use the {@link #createRaw} method.
 */
public class ImageBuilder {

	/**
	 * The debug name of the image to be created (when <i>VK_EXT_debug_utils</i> is enabled)
	 */
	public final String name;

	/**
	 * The size of the image to be created, in pixels, which is part of {@link VkImageCreateInfo#extent()}
	 */
	public final int width, height;

	/**
	 * The depth of the image, in pixels, which is part of {@link VkImageCreateInfo#extent()}.
	 * The default value is 1.
	 */
	public int depth = 1;

	/**
	 * {@link VkImageCreateInfo#imageType()} The default value is <i>VK_IMAGE_TYPE_2D</i>
	 */
	public int type = VK_IMAGE_TYPE_2D;

	/**
	 * {@link VkImageCreateInfo#mipLevels()} The default value is 1.
	 */
	public int mipLevels = 1;

	/**
	 * {@link VkImageCreateInfo#arrayLayers()} The default value is 1.
	 */
	public int arrayLayers = 1;

	/**
	 * {@link VkImageCreateInfo#samples()} The default value is <i>VK_SAMPLE_COUNT_1_BIT</i>
	 */
	public int sampleCount = VK_SAMPLE_COUNT_1_BIT;

	/**
	 * {@link VkImageCreateInfo#tiling()} The default value is <i>VK_IMAGE_TILING_OPTIMAL</i>
	 */
	public int tiling = VK_IMAGE_TILING_OPTIMAL;

	/**
	 * {@link VkImageCreateInfo#sharingMode()} The default value is <i>VK_SHARING_MODE_EXCLUSIVE</i>
	 */
	public int sharingMode = VK_SHARING_MODE_EXCLUSIVE;

	/**
	 * {@link VkImageCreateInfo#initialLayout()} The default value is <i>VK_IMAGE_LAYOUT_UNDEFINED</i>
	 */
	public int initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;

	/**
	 * {@link VkImageCreateInfo#format()}
	 */
	public int format;

	/**
	 * {@link VkImageCreateInfo#usage()}
	 */
	public int usage;

	/**
	 * This function will be invoked to choose a memory type from the memory types allowed by
	 * <b>vkGetImageMemoryRequirements</b>
	 */
	public MemoryTypeSelector memoryTypeSelector = (instance, memoryTypeBits) ->
			instance.memoryInfo.recommendedDeviceLocalMemoryType(memoryTypeBits);

	/**
	 * Will be propagated to {@link VkbImage#aspectMask}
	 * The default value is <i>VK_IMAGE_ASPECT_COLOR_BIT</i>
	 */
	public int aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;

	/**
	 * When the image is being created using {@link com.github.knokko.boiler.memory.MemoryCombiner}, this field
	 * determines whether a corresponding <b>VkImageView</b> will also be created. This is <b>true</b> by default.
	 */
	public boolean shouldCreateView = true;

	/**
	 * @param name The debug name of the image to be created (when <i>VK_EXT_debug_utils</i> is enabled)
	 * @param width The width of the image to be created, in pixels
	 * @param height The height of the image to be created, in pixels
	 */
	public ImageBuilder(String name, int width, int height) {
		this.name = name;
		this.width = width;
		this.height = height;
	}

	/**
	 * Sets {@link #depth}
	 * @return this
	 */
	public ImageBuilder imageDepth(int depth) {
		this.depth = depth;
		return this;
	}

	/**
	 * Sets {@link #type}
	 * @return this
	 */
	public ImageBuilder type(int type) {
		this.type = type;
		return this;
	}

	/**
	 * Sets {@link #mipLevels}
	 * @return this
	 */
	public ImageBuilder mipLevels(int levels) {
		this.mipLevels = levels;
		return this;
	}

	/**
	 * Sets {@link #arrayLayers}
	 * @return this
	 */
	public ImageBuilder arrayLayers(int layerCount) {
		this.arrayLayers = layerCount;
		return this;
	}

	/**
	 * Sets {@link #sampleCount}
	 * @return this
	 */
	public ImageBuilder sampleCount(int sampleCount) {
		this.sampleCount = sampleCount;
		return this;
	}

	/**
	 * Sets {@link #tiling}
	 * @return this
	 */
	public ImageBuilder tiling(int tiling) {
		this.tiling = tiling;
		return this;
	}

	/**
	 * Sets {@link #format}
	 * @return this
	 */
	public ImageBuilder format(int vkFormat) {
		this.format = vkFormat;
		return this;
	}

	/**
	 * Sets {@link #usage}
	 * @return this
	 */
	public ImageBuilder setUsage(int usage) {
		this.usage = usage;
		return this;
	}

	/**
	 * Adds {@code usage} to {@code this.usage} by ORing it
	 * @return this
	 */
	public ImageBuilder addUsage(int usage) {
		this.usage |= usage;
		return this;
	}

	/**
	 * Sets {@link #aspectMask}
	 * @return this
	 */
	public ImageBuilder aspectMask(int aspectMask) {
		this.aspectMask = aspectMask;
		return this;
	}

	/**
	 * Sets {@link #memoryTypeSelector}
	 * @return this
	 */
	public ImageBuilder memoryTypeSelector(MemoryTypeSelector memoryTypeSelector) {
		this.memoryTypeSelector = memoryTypeSelector;
		return this;
	}

	/**
	 * Sets {@link #shouldCreateView} (which is <b>true</b> by default) to <b>false</b>
	 * @return this
	 */
	public ImageBuilder doNotCreateView() {
		this.shouldCreateView = false;
		return this;
	}

	/**
	 * <ul>
	 *     <li>Sets {@code this.format} to {@code format}</li>
	 *     <li>Sets {@link #usage} to <i>VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT</i></li>
	 *     <li>Sets {@link #aspectMask} to <i>VK_IMAGE_ASPECT_DEPTH_BIT</i></li>
	 * </ul>
	 * @param format The <i>VkFormat</i>, which should be a depth(-stencil) format
	 * @return this
	 */
	public ImageBuilder depthAttachment(int format) {
		return this.format(format).setUsage(VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT).aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT);
	}

	/**
	 * <ul>
	 *     <li>Sets {@code this.format} to <i>VK_FORMAT_R8G8B8A8_SRGB</i></li>
	 *     <li>Adds <i>VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT</i> to {@link #usage}</li>
	 * </ul>
	 * @return this
	 */
	public ImageBuilder colorAttachment() {
		return this.format(VK_FORMAT_R8G8B8A8_SRGB).addUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);
	}

	/**
	 * <ul>
	 *     <li>
	 *         Sets {@link #format} to <i>VK_FORMAT_R8G8B8A8_SRGB</i>
	 *         (which you can override later using {@link #format(int)}
	 *     </li>
	 *     <li>Sets {@link #usage} to <i>VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT</i></li>
	 * </ul>
	 * @return this
	 */
	public ImageBuilder texture() {
		return this.format(VK_FORMAT_R8G8B8A8_SRGB).setUsage(VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT);
	}

	/**
	 * Creates a <b>VkImage</b> using the parameters of this ImageBuilder, without binding its memory, or creating a
	 * corresponding <b>VkImageView</b>. Note that using this method is possible, but not the recommended approach to
	 * create {@link VkbImage}s. Consider using {@link com.github.knokko.boiler.memory.MemoryCombiner} instead.
	 * @return a {@link VkbImage} whose fields contain the created <b>VkImage</b>, as well as the width, height, and
	 * aspect mask
	 */
	public VkbImage createRaw(BoilerInstance instance) {
		try (var stack = stackPush()) {
			var ciImage = VkImageCreateInfo.calloc(stack);
			ciImage.sType$Default();
			ciImage.imageType(type);
			ciImage.format(format);
			ciImage.extent().set(width, height, depth);
			ciImage.mipLevels(mipLevels);
			ciImage.arrayLayers(arrayLayers);
			ciImage.samples(sampleCount);
			ciImage.tiling(tiling);
			ciImage.usage(usage);
			ciImage.sharingMode(sharingMode);
			ciImage.initialLayout(initialLayout);

			var pImage = stack.callocLong(1);

			assertVkSuccess(vkCreateImage(
					instance.vkDevice(), ciImage, CallbackUserData.IMAGE.put(stack, instance), pImage
			), "CreateImage", name);
			long vkImage = pImage.get(0);

			instance.debug.name(stack, vkImage, VK_OBJECT_TYPE_IMAGE, name);
			return new VkbImage(vkImage, width, height, aspectMask);
		}
	}

	/**
	 * Creates a <b>VkImageView</b> using the given <i>VkImage</i> handle and the parameters of this
	 * ImageBuilder.
	 */
	public long createView(BoilerInstance instance, long vkImage) {
		try (var stack = stackPush()) {
			var ciImageView = VkImageViewCreateInfo.calloc(stack);
			ciImageView.sType$Default();
			ciImageView.image(vkImage);
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
					instance.vkDevice(), ciImageView, CallbackUserData.IMAGE_VIEW.put(stack, instance), pImageView
			), "CreateImageView", name);
			long imageView = pImageView.get(0);
			instance.debug.name(stack, imageView, VK_OBJECT_TYPE_IMAGE_VIEW, name);
			return imageView;
		}
	}
}
