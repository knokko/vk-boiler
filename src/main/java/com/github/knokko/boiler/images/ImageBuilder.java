package com.github.knokko.boiler.images;

import com.github.knokko.boiler.BoilerInstance;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.VkImageCreateInfo;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVmaSuccess;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.vma.Vma.VMA_MEMORY_USAGE_AUTO;
import static org.lwjgl.util.vma.Vma.vmaCreateImage;
import static org.lwjgl.vulkan.VK10.*;

/**
 * The ImageBuilder class can be used to create (and optionally bind) <i>VkImage</i>s with little code. It is a simple
 * builder class with common default values. The basic flow is:
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
 *     <li>
 *         Call the {@link #build} image to create the <i>VkImage</i>.
 *     </li>
 * </ol>
 * By default, the {@link #build} image will also create an image view, and bind the image memory
 * using VMA. You can use {@link #doNotCreateView()} and {@link #doNotBindMemory()} to override this.
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
	 * Will be propagated to {@link VkbImage#aspectMask()}
	 * The default value is <i>VK_IMAGE_ASPECT_COLOR_BIT</i>
	 */
	public int aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;

	/**
	 * Whether the {@link #build} method should use VMA to bind the memory of the created image.
	 * This is true by default. If you change it to false, the image memory will be unbound.
	 */
	public boolean shouldBindMemory = true;

	/**
	 * Whether the {@link #build} method should create a corresponding <i>VkImageView</i>
	 * for the created image. This is true by default.
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
	 * Sets {@link #shouldBindMemory} (and {@link #shouldCreateView}) to false
	 * @return this
	 */
	public ImageBuilder doNotBindMemory() {
		this.shouldBindMemory = false;
		return this.doNotCreateView();
	}

	/**
	 * Sets {@link #shouldCreateView} to false
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
	 * Builds the image.
	 * <ul>
	 *     <li>If {@link #shouldCreateView} is still true, also creates a corresponding image view</li>
	 *     <li>If {@link #shouldBindMemory} is still true, also binds the image memory using VMA</li>
	 * </ul>
	 */
	public VkbImage build(BoilerInstance instance) {
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
			long allocation = VK_NULL_HANDLE;

			if (shouldBindMemory) {
				var ciAllocation = VmaAllocationCreateInfo.calloc(stack);
				ciAllocation.usage(VMA_MEMORY_USAGE_AUTO);

				var pAllocation = stack.callocPointer(1);
				assertVmaSuccess(vmaCreateImage(
						instance.vmaAllocator(), ciImage, ciAllocation, pImage, pAllocation, null
				), "CreateImage", name);
				allocation = pAllocation.get(0);
			} else {
				assertVkSuccess(vkCreateImage(
						instance.vkDevice(), ciImage, null, pImage
				), "CreateImage", name);
			}

			long image = pImage.get(0);

			instance.debug.name(stack, image, VK_OBJECT_TYPE_IMAGE, name);

			long view = shouldCreateView ? instance.images.createSimpleView(image, format, aspectMask, name) : 0L;
			return new VkbImage(image, view, allocation, width, height, aspectMask);
		}
	}
}
