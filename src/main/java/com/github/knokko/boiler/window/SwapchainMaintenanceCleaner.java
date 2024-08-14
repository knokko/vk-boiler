package com.github.knokko.boiler.window;

import com.github.knokko.boiler.instance.BoilerInstance;
import com.github.knokko.boiler.sync.VkbFence;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkSwapchainPresentFenceInfoEXT;

class SwapchainMaintenanceCleaner implements SwapchainCleaner {

	private final BoilerInstance instance;

	SwapchainMaintenanceCleaner(BoilerInstance instance) {
		this.instance = instance;
	}

	@Override
	public void onAcquire(AcquiredImage acquiredImage) {

	}

	@Override
	public void onChangeCurrentSwapchain(VkbSwapchain oldSwapchain, VkbSwapchain newSwapchain) {

	}

	@Override
	public VkbFence getPresentFence() {
		return null;
	}

	@Override
	public void beforePresent(MemoryStack stack, VkPresentInfoKHR presentInfo, AcquiredImage acquiredImage) {
		acquiredImage.presentFence.waitAndReset(stack);
		//acquiredSwapchain.images[acquired.imageIndex()].didDrawingFinish = didDrawingFinish;

		var fiPresent = VkSwapchainPresentFenceInfoEXT.calloc(stack);
		fiPresent.sType$Default();
		fiPresent.pFences(stack.longs(acquiredImage.presentFence.getVkFenceAndSubmit()));

		presentInfo.pNext(fiPresent);
	}

	@Override
	public void destroyNow() {

	}
}
