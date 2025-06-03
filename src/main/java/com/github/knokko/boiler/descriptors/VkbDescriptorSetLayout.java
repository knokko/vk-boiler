package com.github.knokko.boiler.descriptors;

/**
 * This is a thin wrapper around a <b>VkDescriptorSetLayout</b>. It stores the number of descriptors of each descriptor
 * type that is needed by the layout, which is needed by {@link DescriptorCombiner}. Use
 * {@link DescriptorSetLayoutBuilder} to create instances of this class.
 */
public class VkbDescriptorSetLayout {

	public final long vkDescriptorSetLayout;
	final DescriptorTypeCounter descriptorTypes;

	VkbDescriptorSetLayout(long vkDescriptorSetLayout, DescriptorTypeCounter descriptorTypes) {
		this.vkDescriptorSetLayout = vkDescriptorSetLayout;
		this.descriptorTypes = descriptorTypes.copy();
	}
}
