package com.github.knokko.boiler.sync;

import com.github.knokko.boiler.builder.BoilerBuilder;
import com.github.knokko.boiler.commands.CommandRecorder;
import com.github.knokko.boiler.exceptions.VulkanFailureException;
import com.github.knokko.boiler.instance.BoilerInstance;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures2KHR;
import org.lwjgl.vulkan.VkPhysicalDeviceTimelineSemaphoreFeaturesKHR;
import org.lwjgl.vulkan.VkPhysicalDeviceVulkan12Features;

import static com.github.knokko.boiler.util.CollectionHelper.createSet;
import static java.lang.Thread.sleep;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRGetPhysicalDeviceProperties2.VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRGetPhysicalDeviceProperties2.vkGetPhysicalDeviceFeatures2KHR;
import static org.lwjgl.vulkan.KHRTimelineSemaphore.VK_KHR_TIMELINE_SEMAPHORE_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.VK_API_VERSION_1_2;

public class TestTimelineSemaphores {

	@Test
	public void testUsingVulkan12() throws InterruptedException {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_2, "TestTimelineSemaphores", 1
		)
				.validation()
				.forbidValidationErrors()
				.defaultTimeout(250_000_000L)
				.requiredFeatures12(VkPhysicalDeviceVulkan12Features::timelineSemaphore)
				.featurePicker12((stack, supported, toEnable) -> toEnable.timelineSemaphore(true))
				.build();
		testTimelineSemaphores(instance);
	}

	@Test
	public void testUsingExtension() throws InterruptedException {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_0, "TestTimelineSemaphores", 1
		)
				.validation()
				.forbidValidationErrors()
				.defaultTimeout(250_000_000L)
				.requiredVkInstanceExtensions(createSet(VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME))
				.requiredDeviceExtensions(createSet(VK_KHR_TIMELINE_SEMAPHORE_EXTENSION_NAME))
				.extraDeviceRequirements(((physicalDevice, windowSurface, stack) -> {
					var timelineSupport = VkPhysicalDeviceTimelineSemaphoreFeaturesKHR.calloc(stack);
					timelineSupport.sType$Default();

					var supportedFeatures = VkPhysicalDeviceFeatures2KHR.calloc(stack);
					supportedFeatures.sType$Default();
					supportedFeatures.pNext(timelineSupport);

					vkGetPhysicalDeviceFeatures2KHR(physicalDevice, supportedFeatures);
					return timelineSupport.timelineSemaphore();
				}))
				.beforeDeviceCreation((ciDevice, instanceExtensions, physicalDevice, stack) -> {
					var enableTimeline = VkPhysicalDeviceTimelineSemaphoreFeaturesKHR.calloc(stack);
					enableTimeline.sType$Default();
					enableTimeline.timelineSemaphore(true);

					ciDevice.pNext(enableTimeline);
				})
				.build();
		testTimelineSemaphores(instance);
	}

	private void testTimelineSemaphores(BoilerInstance instance) throws InterruptedException {
		var semaphore = instance.sync.createTimelineSemaphore(1, "Test");
		var fence = instance.sync.fenceBank.borrowFence(false, "TestFence");
		try (var stack = stackPush()) {
			var commandPool = instance.commands.createPool(0, instance.queueFamilies().graphics().index(), "TestPool");
			var commandBuffer = instance.commands.createPrimaryBuffers(commandPool, 1, "TestBuffers")[0];
			CommandRecorder.begin(commandBuffer, instance, stack, "TestRecording").end();

			instance.queueFamilies().graphics().queues().get(0).submit(
					commandBuffer, "TestTimeline", new WaitSemaphore[0], fence, new long[0],
					new WaitTimelineSemaphore[]{
							new WaitTimelineSemaphore(semaphore.vkSemaphore, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 2)
					}, new TimelineInstant(semaphore, 5)
			);

			// The submission can't start yet because the timeline semaphore value is still 1, but needs to be 2
			sleep(100);
			assertEquals(1, semaphore.getValue());

			// When we set the value to 2, the pending submission should start running, and then set it to 5
			semaphore.setValue(2);
			fence.waitAndReset();
			assertEquals(5, semaphore.getValue());

			semaphore.waitUntil(4);

			String functionName = "vkWaitSemaphores";
			if (instance.deviceExtensions.contains(VK_KHR_TIMELINE_SEMAPHORE_EXTENSION_NAME)) {
				functionName += "KHR";
			}
			assertEquals(functionName + " (Test) returned 2 (VK_TIMEOUT)", assertThrows(
					VulkanFailureException.class, () -> semaphore.waitUntil(6)
			).getMessage());
			semaphore.setValue(8);
			assertEquals(8, semaphore.getValue());

			vkDestroyCommandPool(instance.vkDevice(), commandPool, null);
		}

		instance.sync.fenceBank.returnFence(fence);
		semaphore.destroy();
		instance.destroyInitialObjects();
	}
}
