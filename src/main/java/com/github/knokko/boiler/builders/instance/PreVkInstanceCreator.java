package com.github.knokko.boiler.builders.instance;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkInstanceCreateInfo;

@FunctionalInterface
public interface PreVkInstanceCreator {

	void beforeInstanceCreation(VkInstanceCreateInfo ciInstance, MemoryStack stack);
}
