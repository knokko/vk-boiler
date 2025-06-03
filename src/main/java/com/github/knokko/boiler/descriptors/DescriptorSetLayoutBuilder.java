package com.github.knokko.boiler.descriptors;

import com.github.knokko.boiler.BoilerInstance;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.vulkan.VK10.*;

/**
 * This is a thin wrapper around {@link VkDescriptorSetLayoutCreateInfo}, that should make the process of creating
 * <b>VkDescriptorSetLayout</b>s slightly less verbose. Usage:
 * <ol>
 *     <li>Use the public constructor to create an instance of this class.</li>
 *     <li>
 *         Populate the descriptor bindings, by either:
 *         <ul>
 *             <li>calling the {@link #set(int, int, int, int)} method, or</li>
 *             <li>by modifying {@link #ciLayout} and/or {@link #bindings} directly</li>
 *         </ul>
 *     </li>
 *     <li>Call the {@link #build} method</li>
 * </ol>
 */
public class DescriptorSetLayoutBuilder {

	public final VkDescriptorSetLayoutCreateInfo ciLayout;
	public final VkDescriptorSetLayoutBinding.Buffer bindings;
	public final MemoryStack stack;

	/**
	 * @param stack The stack onto which {@link #ciLayout} and {@link #bindings} will be allocated
	 * @param numBindings The capacity of {@link #bindings}
	 */
	public DescriptorSetLayoutBuilder(MemoryStack stack, int numBindings) {
		this.ciLayout = VkDescriptorSetLayoutCreateInfo.calloc(stack);
		this.bindings = VkDescriptorSetLayoutBinding.calloc(numBindings, stack);
		this.stack = stack;
		this.ciLayout.sType$Default();
		this.ciLayout.pBindings(bindings);
	}

	/**
	 * Modifies the {@link VkDescriptorSetLayoutBinding} at the given index, sets:
	 * <ul>
	 *     <li>{@link VkDescriptorSetLayoutBinding#binding()} to {@code binding}</li>
	 *     <li>{@link VkDescriptorSetLayoutBinding#descriptorType()} to {@code descriptorType}</li>
	 *     <li>{@link VkDescriptorSetLayoutBinding#descriptorCount()} to 1</li>
	 *     <li>{@link VkDescriptorSetLayoutBinding#stageFlags()} to {@code shaderStage}</li>
	 * </ul>
	 */
	public void set(int index, int binding, int descriptorType, int shaderStage) {
		var target = this.bindings.get(index);
		target.binding(binding);
		target.descriptorType(descriptorType);
		target.descriptorCount(1);
		target.stageFlags(shaderStage);
	}

	/**
	 * Calls {@link org.lwjgl.vulkan.VK10#vkCreateDescriptorSetLayout} using {@link #ciLayout}
	 * @param name The debug name (when validation is enabled)
	 */
	public VkbDescriptorSetLayout build(BoilerInstance instance, String name) {
		var pLayout = stack.callocLong(1);
		assertVkSuccess(vkCreateDescriptorSetLayout(
				instance.vkDevice(), ciLayout, null, pLayout
		), "CreateDescriptorSetLayout", name);
		long vkDescriptorSetLayout = pLayout.get(0);

		instance.debug.name(stack, vkDescriptorSetLayout, VK_OBJECT_TYPE_DESCRIPTOR_SET_LAYOUT, name);

		var descriptorTypes = new DescriptorTypeCounter();
		for (int index = bindings.position(); index < bindings.limit(); index++) {
			descriptorTypes.add(bindings.get(index).descriptorType(), bindings.get(index).descriptorCount());
		}

		return new VkbDescriptorSetLayout(vkDescriptorSetLayout, descriptorTypes);
	}
}
