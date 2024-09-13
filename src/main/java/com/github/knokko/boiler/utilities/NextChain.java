package com.github.knokko.boiler.utilities;

import org.lwjgl.vulkan.VkBaseOutStructure;

public class NextChain {

	/**
	 * Finds the memory address of the struct with the given <i>structureType</i> (<i>sType</i>) into the <i>pNext</i>
	 * chain starting at memory address <i>firstPNext</i>. Returns <b>null</b> if such a struct is not present in the
	 * <i>pNext</i> chain.
	 */
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
