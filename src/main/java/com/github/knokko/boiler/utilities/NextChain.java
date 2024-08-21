package com.github.knokko.boiler.utilities;

import org.lwjgl.vulkan.VkBaseOutStructure;

public class NextChain {

	public static long findAddress(long firstPNext, int structureType) {
		@SuppressWarnings("resource")
		var currentLink = VkBaseOutStructure.createSafe(firstPNext);
		while (currentLink != null) {
			if (currentLink.sType() == structureType) return currentLink.address();
			currentLink = currentLink.pNext();
		}
		return 0L;
	}
}
