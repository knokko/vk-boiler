package com.github.knokko.boiler.window;

import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfacePresentModeCompatibilityEXT;

import java.util.ArrayList;
import java.util.List;

import static com.github.knokko.boiler.utilities.CollectionHelper.createSet;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR;
import static org.lwjgl.vulkan.VK10.*;

public class TestSwapchainManager {

	private List<Long> createList(long... elements) {
		List<Long> list = new ArrayList<>();
		for (long element : elements) list.add(element);
		return list;
	}

	@Test
	public void testWithoutMaintenanceAndWithoutOldSwapchains() {
		var presentModes = new PresentModes(createSet(VK_PRESENT_MODE_FIFO_KHR), createSet(VK_PRESENT_MODE_FIFO_KHR));
		var properties = new WindowProperties(
				1234L, "TestTitle", 12345L, 2, VK_FORMAT_R8G8B8A8_UNORM,
				VK_COLOR_SPACE_SRGB_NONLINEAR_KHR, VK_IMAGE_USAGE_TRANSFER_DST_BIT,
				VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR, false, 0, 2
		);

		var functions = new DummySwapchainFunctions();
		functions.capabilities = VkSurfaceCapabilitiesKHR.create();
		functions.properties = properties;
		functions.numSwapchainImages = 3;

		var associations = new DummyAssociations();
		var swapchains = new SwapchainManager(functions, properties, presentModes);

		functions.expectedSwapchain = 12L;
		functions.expectedAcquireSemaphore = 1L;
		functions.expectedAcquireFence = null;
		functions.nextImageIndex = 1;
		functions.nextAcquireResult = VK_SUCCESS;

		functions.capabilities.currentExtent().set(600, 200);

		// -------------------------acquire 1------------------------------
		assertEquals(12L, functions.nextSwapchain);
		var image1 = swapchains.acquire(VK_PRESENT_MODE_FIFO_KHR, false);
		assertEquals(13L, functions.nextSwapchain);
		assertEquals(12L, image1.swapchain.vkSwapchain);
		assertEquals(1, image1.index);
		assertNull(image1.acquireSubmission);
		assertThrows(UnsupportedOperationException.class, image1::getAcquireSubmission);
		assertEquals(1L, image1.getAcquireSemaphore());
		assertEquals(600, image1.getWidth());
		assertEquals(200, image1.getHeight());
		assertEquals(createSet(1L, 2L), functions.borrowedSemaphores);
		assertEquals(VK_PRESENT_MODE_FIFO_KHR, image1.presentMode);
		assertNull(image1.presentFence);
		assertEquals(2L, image1.getPresentSemaphore());
		// -------------------------acquire 1------------------------------

		var associatedSwapchain1 = associations.getSwapchainAssociation(image1);
		var associatedImage1 = associations.getImageAssociation(image1);
		assertEquals(1, associatedSwapchain1.createdImages);

		// -------------------------present 1------------------------------
		functions.expectedSwitchPresentMode = false;
		functions.nextPresentResult = VK_SUCCESS;
		image1.swapchain.presentImage(image1);
		// -------------------------present 1------------------------------

		// Change swapchain extent to trigger a recreation
		functions.capabilities.currentExtent().set(606, 202);
		functions.expectedSwapchain = 13L;
		functions.expectedAcquireSemaphore = 3L;
		functions.expectedOldSwapchain = 12L;
		functions.expectedAcquireFence = null;
		functions.nextImageIndex = 2;
		functions.nextAcquireResult = VK_SUCCESS;
		assertEquals(0, functions.destroyedSwapchains.size());

		// -------------------------acquire 2------------------------------
		assertEquals(0, functions.deviceWaitIdleCount);
		assertEquals(13L, functions.nextSwapchain);
		var image2 = swapchains.acquire(VK_PRESENT_MODE_FIFO_KHR, false);
		assertEquals(1, functions.deviceWaitIdleCount);
		assertEquals(14L, functions.nextSwapchain);
		assertEquals(13L, image2.swapchain.vkSwapchain);
		assertEquals(2, image2.index);
		assertNull(image2.acquireSubmission);
		assertThrows(UnsupportedOperationException.class, image2::getAcquireSubmission);
		assertEquals(3L, image2.getAcquireSemaphore());
		assertEquals(606, image2.getWidth());
		assertEquals(202, image2.getHeight());

		// Note that semaphore 2 (the first present semaphore) can be returned because we did vkDeviceWaitIdle
		assertEquals(createSet(1L, 3L, 4L), functions.borrowedSemaphores);
		assertEquals(VK_PRESENT_MODE_FIFO_KHR, image2.presentMode);
		assertNull(image2.presentFence);
		assertEquals(4L, image2.getPresentSemaphore());
		// -------------------------acquire 2------------------------------

		assertEquals(1, functions.destroyedSwapchains.size());
		assertTrue(associatedImage1.wasDestroyed);

		// Associated swapchain resources can potentially be recycled, so they are destroyed later
		assertFalse(associatedSwapchain1.wasDestroyed);
		assertFalse(associatedSwapchain1.wasRecycled);

		var associatedSwapchain2 = associations.getSwapchainAssociation(image2);
		var associatedImage2 = associations.getImageAssociation(image2);
		assertEquals(1, associatedSwapchain2.createdImages);

		assertTrue(associatedSwapchain1.wasDestroyed);
		assertTrue(associatedSwapchain1.wasRecycled);

		// -------------------------present 2------------------------------
		functions.expectedSwitchPresentMode = false;
		functions.nextPresentResult = VK_SUCCESS;
		image2.swapchain.presentImage(image2);
		// -------------------------present 2------------------------------

		assertNotSame(associatedSwapchain1, associatedSwapchain2);
		assertNotSame(associatedImage1, associatedImage2);
		assertFalse(associatedSwapchain2.wasDestroyed);
		assertFalse(associatedImage2.wasDestroyed);

		swapchains.destroy();
		assertEquals(2, functions.deviceWaitIdleCount);
		assertEquals(createList(12L, 13L), functions.destroyedSwapchains);
		assertEquals(createSet(), functions.borrowedSemaphores);
		assertEquals(0, functions.nextFence);
		assertTrue(associatedSwapchain2.wasDestroyed);
		assertTrue(associatedImage2.wasDestroyed);
	}

	@Test
	public void testWithoutMaintenanceAndWithOneOldSwapchain() {
		var presentModes = new PresentModes(createSet(VK_PRESENT_MODE_FIFO_KHR), createSet(VK_PRESENT_MODE_FIFO_KHR));
		var properties = new WindowProperties(
				1234L, "TestTitle", 12345L, 0, VK_FORMAT_R8G8B8A8_UNORM,
				VK_COLOR_SPACE_SRGB_NONLINEAR_KHR, VK_IMAGE_USAGE_TRANSFER_DST_BIT,
				VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR, false, 1, 2
		);

		var functions = new DummySwapchainFunctions();
		functions.capabilities = VkSurfaceCapabilitiesKHR.create();
		functions.properties = properties;
		functions.numSwapchainImages = 3;

		var swapchains = new SwapchainManager(functions, properties, presentModes);
		var associations = new DummyAssociations();

		functions.expectedSwapchain = 12L;
		functions.expectedAcquireSemaphore = VK_NULL_HANDLE;
		functions.expectedAcquireFence = functions.availableFences[0];
		functions.nextImageIndex = 2;
		functions.nextAcquireResult = VK_SUBOPTIMAL_KHR;

		functions.capabilities.currentExtent().set(600, 200);

		// -------------------------acquire 1------------------------------
		// In this scenario, vkAcquireNextImageKHR will return VK_SUBOPTIMAL_KHR, which means that the current frame
		// continues, but that the swapchain should be recreated after this frame.

		assertEquals(12L, functions.nextSwapchain);
		assertFalse(functions.availableFences[0].isPending());
		var image1 = swapchains.acquire(VK_PRESENT_MODE_FIFO_KHR, true);
		assertEquals(13L, functions.nextSwapchain);
		assertEquals(12L, image1.swapchain.vkSwapchain);
		assertEquals(2, image1.index);
		assertNotNull(image1.getAcquireSubmission());
		assertThrows(UnsupportedOperationException.class, image1::getAcquireSemaphore);
		assertEquals(600, image1.getWidth());
		assertEquals(200, image1.getHeight());
		assertEquals(createSet(1L), functions.borrowedSemaphores);
		assertEquals(VK_PRESENT_MODE_FIFO_KHR, image1.presentMode);
		assertNull(image1.presentFence);
		assertEquals(1L, image1.getPresentSemaphore());
		assertTrue(functions.availableFences[0].isPending());
		// -------------------------acquire 1------------------------------

		var associatedSwapchain1 = associations.getSwapchainAssociation(image1);
		var associatedImage1 = associations.getImageAssociation(image1);

		// -------------------------present 1------------------------------
		functions.availableFences[0].forceSignal();
		functions.expectedSwitchPresentMode = false;
		functions.nextPresentResult = VK_SUCCESS;
		image1.swapchain.presentImage(image1);
		// -------------------------present 1------------------------------

		// -------------------------acquire 2------------------------------
		// The swapchain management system should recreate the swapchain because the previous acquire result was
		// VK_SUBOPTIMAL_KHR. Since this scenario allows 1 old swapchain, we do NOT call vkDeviceWaitIdle, but try to
		// use an acquire fence to find out when the old swapchain can be destroyed.

		// Note that the application will NOT ask for an acquire fence, so the system needs to do this behind the
		// scenes. However, not in the first frame, since only the SECOND acquire of a swapchain image is interesting.
		functions.expectedSwapchain = 13L;
		functions.expectedAcquireSemaphore = 2L;
		functions.expectedAcquireFence = null;
		functions.nextImageIndex = 1;
		functions.nextAcquireResult = VK_SUCCESS;
		functions.expectedOldSwapchain = 12L;
		assertEquals(0, functions.deviceWaitIdleCount);
		assertEquals(13L, functions.nextSwapchain);
		var image2 = swapchains.acquire(VK_PRESENT_MODE_FIFO_KHR, false);

		assertEquals(0, functions.deviceWaitIdleCount);
		assertEquals(14L, functions.nextSwapchain);
		assertEquals(13L, image2.swapchain.vkSwapchain);
		assertEquals(1, image2.index);
		assertNull(image2.acquireSubmission);
		assertThrows(UnsupportedOperationException.class, image2::getAcquireSubmission);
		assertEquals(2L, image2.getAcquireSemaphore());
		assertEquals(600, image2.getWidth());
		assertEquals(200, image2.getHeight());

		assertEquals(createSet(1L, 2L, 3L), functions.borrowedSemaphores);
		assertEquals(VK_PRESENT_MODE_FIFO_KHR, image2.presentMode);
		assertNull(image2.presentFence);
		assertEquals(3L, image2.getPresentSemaphore());
		// -------------------------acquire 2------------------------------

		// -------------------------present 2------------------------------
		functions.expectedSwitchPresentMode = false;
		functions.nextPresentResult = VK_SUCCESS;
		image2.swapchain.presentImage(image2);
		// -------------------------present 2------------------------------

		// -------------------------acquire 3------------------------------
		// Image 1 has been acquired in the previous frame, so the old swapchain can be destroyed after image 1 is
		// re-acquired. However, the swapchain system does NOT know which image is acquired next, so the acquire fence
		// may or may not be useful. In this case, it is not, since image 2 is acquired next.
		functions.expectedSwapchain = 13L;
		functions.expectedAcquireSemaphore = 4L;
		functions.expectedAcquireFence = functions.availableFences[0];
		functions.nextImageIndex = 2;
		functions.nextAcquireResult = VK_SUCCESS;
		assertEquals(0, functions.deviceWaitIdleCount);
		assertEquals(14L, functions.nextSwapchain);
		assertFalse(functions.availableFences[0].isPending());
		var image3 = swapchains.acquire(VK_PRESENT_MODE_FIFO_KHR, false);

		assertEquals(0, functions.deviceWaitIdleCount);
		assertEquals(14L, functions.nextSwapchain);
		assertEquals(13L, image3.swapchain.vkSwapchain);
		assertEquals(2, image3.index);
		assertNotNull(image3.acquireSubmission);
		assertThrows(UnsupportedOperationException.class, image3::getAcquireSubmission);
		assertEquals(4L, image3.getAcquireSemaphore());
		assertEquals(600, image3.getWidth());
		assertEquals(200, image3.getHeight());

		assertEquals(createSet(1L, 2L, 3L, 4L, 5L), functions.borrowedSemaphores);
		assertEquals(VK_PRESENT_MODE_FIFO_KHR, image3.presentMode);
		assertNull(image3.presentFence);
		assertEquals(5L, image3.getPresentSemaphore());
		assertTrue(functions.availableFences[0].isPending());
		// -------------------------acquire 3------------------------------

		var associatedSwapchain3 = associations.getSwapchainAssociation(image3);
		var associatedImage3 = associations.getImageAssociation(image3);

		// Note that we can NOT destroy the old associations before the old swapchain is destroyed
		assertFalse(associatedSwapchain1.wasDestroyed);
		assertFalse(associatedSwapchain1.wasRecycled);
		assertFalse(associatedImage1.wasDestroyed);

		// -------------------------present 3------------------------------
		functions.availableFences[0].forceSignal();
		functions.expectedSwitchPresentMode = false;
		functions.nextPresentResult = VK_SUCCESS;
		image3.swapchain.presentImage(image3);
		// -------------------------present 3------------------------------

		// -------------------------acquire 4------------------------------
		// Both image 1 and 2 have been acquired previously, so we can destroy the swapchain after any of them is
		// re-acquired. In this case, it is not, since image 1 is acquired next, so the old swapchain can be destroyed
		// after that fence is signaled.
		functions.expectedSwapchain = 13L;
		functions.expectedAcquireSemaphore = 6L;
		functions.expectedAcquireFence = functions.availableFences[0];
		functions.nextImageIndex = 1;
		functions.nextAcquireResult = VK_SUCCESS;
		assertEquals(0, functions.deviceWaitIdleCount);
		assertEquals(14L, functions.nextSwapchain);
		assertFalse(functions.availableFences[0].isPending());
		var image4 = swapchains.acquire(VK_PRESENT_MODE_FIFO_KHR, false);

		assertEquals(0, functions.deviceWaitIdleCount);
		assertEquals(14L, functions.nextSwapchain);
		assertEquals(13L, image4.swapchain.vkSwapchain);
		assertEquals(1, image4.index);
		assertNotNull(image4.acquireSubmission);
		assertThrows(UnsupportedOperationException.class, image4::getAcquireSubmission);
		assertEquals(6L, image4.getAcquireSemaphore());
		assertEquals(600, image4.getWidth());
		assertEquals(200, image4.getHeight());

		assertEquals(createSet(1L, 2L, 3L, 4L, 5L, 6L), functions.borrowedSemaphores);
		assertEquals(VK_PRESENT_MODE_FIFO_KHR, image4.presentMode);
		assertNull(image4.presentFence);
		assertEquals(3L, image4.getPresentSemaphore());
		assertTrue(functions.availableFences[0].isPending());
		// -------------------------acquire 4------------------------------

		// -------------------------present 4------------------------------
		functions.availableFences[0].forceSignal();
		functions.expectedSwitchPresentMode = false;
		functions.nextPresentResult = VK_SUCCESS;
		image4.swapchain.presentImage(image4);
		// -------------------------present 4------------------------------

		// -------------------------acquire 5------------------------------
		// Since the previous acquire fence has been signaled, we can finally destroy the old swapchain
		functions.expectedSwapchain = 13L;
		functions.expectedAcquireSemaphore = 2L;
		functions.expectedAcquireFence = null;
		functions.nextImageIndex = 0;
		functions.nextAcquireResult = VK_SUCCESS;
		assertEquals(0, functions.deviceWaitIdleCount);
		assertEquals(14L, functions.nextSwapchain);
		assertEquals(0, functions.destroyedSwapchains.size());
		var image5 = swapchains.acquire(VK_PRESENT_MODE_FIFO_KHR, false);

		assertEquals(1, functions.destroyedSwapchains.size());
		assertFalse(functions.availableFences[0].isPending());

		assertEquals(0, functions.deviceWaitIdleCount);
		assertEquals(14L, functions.nextSwapchain);
		assertEquals(13L, image5.swapchain.vkSwapchain);
		assertEquals(0, image5.index);
		assertNull(image5.acquireSubmission);
		assertThrows(UnsupportedOperationException.class, image5::getAcquireSubmission);
		assertEquals(2L, image5.getAcquireSemaphore());
		assertEquals(600, image5.getWidth());
		assertEquals(200, image5.getHeight());

		assertEquals(createSet(2L, 3L, 4L, 5L, 6L, 7L), functions.borrowedSemaphores);
		assertEquals(VK_PRESENT_MODE_FIFO_KHR, image5.presentMode);
		assertNull(image5.presentFence);
		assertEquals(7L, image5.getPresentSemaphore());
		assertFalse(functions.availableFences[0].isPending());
		// -------------------------acquire 5------------------------------

		assertTrue(associatedImage1.wasDestroyed);
		assertFalse(associatedSwapchain1.wasDestroyed);

		var associatedSwapchain5 = associations.getSwapchainAssociation(image5);
		var associatedImage5 = associations.getImageAssociation(image5);

		// -------------------------present 5------------------------------
		functions.nextPresentResult = VK_ERROR_OUT_OF_DATE_KHR;
		image5.swapchain.presentImage(image5);
		// -------------------------present 5------------------------------


		// -------------------------acquire 6------------------------------
		functions.expectedOldSwapchain = 13L;
		functions.expectedSwapchain = 14L;
		functions.nextSwapchain = 14L;
		functions.expectedAcquireSemaphore = VK_NULL_HANDLE;
		functions.expectedAcquireFence = functions.availableFences[0];
		var image6 = swapchains.acquire(VK_PRESENT_MODE_FIFO_KHR, true);
		// -------------------------acquire 6------------------------------

		assertFalse(associatedSwapchain1.wasDestroyed);
		var associatedSwapchain6 = associations.getSwapchainAssociation(image6);
		var associatedImage6 = associations.getImageAssociation(image6);

		assertTrue(associatedSwapchain1.wasDestroyed);
		assertTrue(associatedSwapchain1.wasRecycled);
		assertFalse(associatedSwapchain3.wasDestroyed);

		// -------------------------present 6------------------------------
		image5.swapchain.presentImage(image5);
		// -------------------------present 6------------------------------

		assertEquals(1, functions.destroyedSwapchains.size());
		assertEquals(0, functions.deviceWaitIdleCount);
		assertFalse(associatedImage3.wasDestroyed);
		assertFalse(associatedSwapchain3.wasDestroyed);
		assertFalse(associatedSwapchain6.wasDestroyed);

		swapchains.destroy();

		assertEquals(1, functions.deviceWaitIdleCount);
		assertEquals(3, functions.destroyedSwapchains.size());
		assertTrue(associatedSwapchain3.wasDestroyed);
		assertTrue(associatedSwapchain5.wasDestroyed);
		assertTrue(associatedSwapchain6.wasDestroyed);
		assertFalse(associatedSwapchain3.wasRecycled);
		assertFalse(associatedSwapchain5.wasRecycled);
		assertFalse(associatedSwapchain6.wasRecycled);
		assertTrue(associatedImage5.wasDestroyed);
		assertTrue(associatedImage6.wasDestroyed);
	}

	@Test
	public void testWithMaintenanceAndWithoutOldSwapchains() {
		var presentModes = new PresentModes(
				createSet(VK_PRESENT_MODE_FIFO_KHR, VK_PRESENT_MODE_IMMEDIATE_KHR, VK_PRESENT_MODE_MAILBOX_KHR),
				createSet(VK_PRESENT_MODE_FIFO_KHR, VK_PRESENT_MODE_MAILBOX_KHR, VK_PRESENT_MODE_FIFO_RELAXED_KHR)
		);
		var properties = new WindowProperties(
				1234L, "TestTitle", 12345L, 4, VK_FORMAT_R8G8B8A8_UNORM,
				VK_COLOR_SPACE_SRGB_NONLINEAR_KHR, VK_IMAGE_USAGE_TRANSFER_DST_BIT,
				VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR, true, 0, 1
		);

		var functions = new DummySwapchainFunctions();
		functions.capabilities = VkSurfaceCapabilitiesKHR.create();
		functions.properties = properties;
		functions.numSwapchainImages = 4;

		functions.presentModeCompatibility = VkSurfacePresentModeCompatibilityEXT.create();
		functions.presentModeCompatibility.presentModeCount(3);
		functions.presentModeCompatibility.pPresentModes(
				BufferUtils.createIntBuffer(2)
						.put(VK_PRESENT_MODE_MAILBOX_KHR)
						.put(VK_PRESENT_MODE_IMMEDIATE_KHR).flip()
		);

		var associations = new DummyAssociations();
		var swapchains = new SwapchainManager(functions, properties, presentModes);

		functions.expectedSwapchain = 12L;
		functions.expectedAcquireSemaphore = 1L;
		functions.expectedAcquireFence = null;
		functions.nextImageIndex = 3;
		functions.nextAcquireResult = VK_SUCCESS;

		functions.capabilities.currentExtent().set(600, 200);

		// -------------------------acquire 1------------------------------
		assertEquals(12L, functions.nextSwapchain);
		var image1 = swapchains.acquire(VK_PRESENT_MODE_FIFO_KHR, false);
		assertEquals(13L, functions.nextSwapchain);
		assertEquals(12L, image1.swapchain.vkSwapchain);
		assertEquals(3, image1.index);
		assertNull(image1.acquireSubmission);
		assertThrows(UnsupportedOperationException.class, image1::getAcquireSubmission);
		assertEquals(1L, image1.getAcquireSemaphore());
		assertEquals(600, image1.getWidth());
		assertEquals(200, image1.getHeight());
		assertEquals(createSet(1L, 2L), functions.borrowedSemaphores);
		assertEquals(VK_PRESENT_MODE_FIFO_KHR, image1.presentMode);
		assertNull(image1.presentFence);
		assertEquals(2L, image1.getPresentSemaphore());
		// -------------------------acquire 1------------------------------

		var associatedSwapchain1 = associations.getSwapchainAssociation(image1);
		var associatedImage1 = associations.getImageAssociation(image1);
		assertEquals(1, associatedSwapchain1.createdImages);

		// -------------------------present 1------------------------------
		functions.expectedSwitchPresentMode = false;
		functions.nextPresentResult = VK_SUCCESS;
		image1.swapchain.presentImage(image1);
		// -------------------------present 1------------------------------

		// -------------------------acquire 2------------------------------
		// In this acquire, we request MAILBOX instead of FIFO, which should cause a present-mode switch,
		// but no swapchain recreation

		functions.expectedAcquireSemaphore = VK_NULL_HANDLE;
		functions.expectedAcquireFence = functions.availableFences[0];
		functions.nextImageIndex = 2;

		var image2 = swapchains.acquire(VK_PRESENT_MODE_MAILBOX_KHR, true);
		assertEquals(13L, functions.nextSwapchain);
		assertEquals(12L, image2.swapchain.vkSwapchain);
		assertEquals(2, image2.index);
		assertNotNull(image2.getAcquireSubmission());
		assertThrows(UnsupportedOperationException.class, image2::getAcquireSemaphore);
		assertEquals(600, image2.getWidth());
		assertEquals(200, image2.getHeight());
		assertEquals(createSet(1L, 2L, 3L), functions.borrowedSemaphores);
		assertEquals(VK_PRESENT_MODE_MAILBOX_KHR, image2.presentMode);
		assertNull(image2.presentFence);
		assertEquals(3L, image2.getPresentSemaphore());
		// -------------------------acquire 2------------------------------

		functions.availableFences[0].forceSignal();

		var associatedSwapchain2 = associations.getSwapchainAssociation(image2);
		var associatedImage2 = associations.getImageAssociation(image2);
		assertEquals(2, associatedSwapchain1.createdImages);
		assertSame(associatedSwapchain1, associatedSwapchain2);
		assertNotSame(associatedImage1, associatedImage2);

		// -------------------------present 2------------------------------
		functions.expectedSwitchPresentMode = true;
		functions.nextPresentResult = VK_SUCCESS;
		image2.swapchain.presentImage(image2);
		// -------------------------present 2------------------------------

		assertEquals(0, functions.deviceWaitIdleCount);
		functions.expectedSwapchain = 13L;

		// -------------------------acquire 3------------------------------
		// In this acquire, we request IMMEDIATE instead of MAILBOX, which should cause a swapchain recreation,
		// because we didn't request this present mode before

		functions.expectedAcquireSemaphore = VK_NULL_HANDLE;
		functions.expectedOldSwapchain = 12L;
		functions.expectedAcquireFence = functions.availableFences[0];
		functions.nextImageIndex = 3;
		functions.presentModeCompatibility.presentModeCount(2);
		functions.presentModeCompatibility.pPresentModes(
				// Intentionally make it NOT compatible with FIFO, forcing a swapchain recreation in acquire 4
				BufferUtils.createIntBuffer(1).put(VK_PRESENT_MODE_MAILBOX_KHR)
		);

		var image3 = swapchains.acquire(VK_PRESENT_MODE_IMMEDIATE_KHR, true);
		assertEquals(14L, functions.nextSwapchain);
		assertEquals(13L, image3.swapchain.vkSwapchain);
		assertEquals(3, image3.index);
		assertNotNull(image3.getAcquireSubmission());
		assertThrows(UnsupportedOperationException.class, image3::getAcquireSemaphore);
		assertEquals(600, image3.getWidth());
		assertEquals(200, image3.getHeight());
		// semaphores 2 and 3 belonged to the old destroyed swapchain
		assertEquals(createSet(1L, 4L), functions.borrowedSemaphores);
		assertEquals(VK_PRESENT_MODE_IMMEDIATE_KHR, image3.presentMode);
		assertNull(image3.presentFence);
		assertEquals(4L, image3.getPresentSemaphore());
		// -------------------------acquire 3------------------------------

		functions.availableFences[0].forceSignal();
		assertEquals(1, functions.deviceWaitIdleCount);
		assertEquals(1, functions.destroyedSwapchains.size());

		assertFalse(associatedSwapchain1.wasDestroyed);
		assertFalse(associatedSwapchain1.wasRecycled);
		assertTrue(associatedImage1.wasDestroyed);

		var associatedSwapchain3 = associations.getSwapchainAssociation(image3);
		var associatedImage3 = associations.getImageAssociation(image3);
		assertEquals(2, associatedSwapchain1.createdImages);
		assertEquals(1, associatedSwapchain3.createdImages);
		assertNotSame(associatedSwapchain2, associatedSwapchain3);
		assertNotSame(associatedImage2, associatedImage3);

		assertTrue(associatedSwapchain1.wasDestroyed);
		assertTrue(associatedSwapchain1.wasRecycled);

		// -------------------------present 3------------------------------
		functions.expectedSwitchPresentMode = false;
		functions.nextPresentResult = VK_SUCCESS;
		image3.swapchain.presentImage(image3);
		// -------------------------present 3------------------------------

		// -------------------------acquire 4------------------------------
		// In this acquire, we request FIFO instead of IMMEDIATE, which should cause a swapchain recreation because
		// FIFO was not in the list of present modes compatible with IMMEDIATE

		functions.expectedAcquireSemaphore = 5L;
		functions.expectedAcquireFence = null;
		functions.nextImageIndex = 1;
		functions.expectedSwapchain = 14L;
		functions.expectedOldSwapchain = 13L;

		var image4 = swapchains.acquire(VK_PRESENT_MODE_FIFO_KHR, false);
		assertEquals(15L, functions.nextSwapchain);
		assertEquals(14L, image4.swapchain.vkSwapchain);
		assertEquals(1, image4.index);
		assertNull(image4.acquireSubmission);
		assertThrows(UnsupportedOperationException.class, image4::getAcquireSubmission);
		assertEquals(5L, image4.getAcquireSemaphore());
		assertEquals(600, image4.getWidth());
		assertEquals(200, image4.getHeight());
		// Semaphores 3 and 4 belong to the previous swapchain, which was destroyed
		assertEquals(createSet(1L, 5L, 6L), functions.borrowedSemaphores);
		assertEquals(VK_PRESENT_MODE_FIFO_KHR, image4.presentMode);
		assertNull(image4.presentFence);
		assertEquals(6L, image4.getPresentSemaphore());
		// -------------------------acquire 4------------------------------

		functions.availableFences[0].forceSignal();
		assertEquals(2, functions.deviceWaitIdleCount);
		assertEquals(2, functions.destroyedSwapchains.size());

		assertFalse(associatedSwapchain3.wasDestroyed);
		assertFalse(associatedSwapchain3.wasRecycled);
		assertTrue(associatedImage3.wasDestroyed);

		// -------------------------present 4------------------------------
		functions.expectedSwitchPresentMode = false;
		functions.nextPresentResult = VK_SUCCESS;
		image4.swapchain.presentImage(image4);
		// -------------------------present 4------------------------------

		swapchains.destroy();
		assertEquals(3, functions.deviceWaitIdleCount);
		assertEquals(3, functions.destroyedSwapchains.size());

		assertTrue(associatedSwapchain3.wasDestroyed);
		assertFalse(associatedSwapchain3.wasRecycled);
		assertEquals(0, functions.borrowedSemaphores.size());
	}

	@Test
	public void testWithMaintenanceAndWithOneOldSwapchain() {
		var presentModes = new PresentModes(createSet(VK_PRESENT_MODE_FIFO_KHR), createSet(VK_PRESENT_MODE_FIFO_KHR));
		var properties = new WindowProperties(
				1234L, "TestTitle", 12345L, 0, VK_FORMAT_R8G8B8A8_UNORM,
				VK_COLOR_SPACE_SRGB_NONLINEAR_KHR, VK_IMAGE_USAGE_TRANSFER_DST_BIT,
				VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR, true, 1, 2
		);

		var functions = new DummySwapchainFunctions();
		functions.capabilities = VkSurfaceCapabilitiesKHR.create();
		functions.properties = properties;
		functions.numSwapchainImages = 3;
		functions.presentModeCompatibility = VkSurfacePresentModeCompatibilityEXT.create();
		functions.presentModeCompatibility.presentModeCount(0);

		var swapchains = new SwapchainManager(functions, properties, presentModes);
		var associations = new DummyAssociations();

		functions.expectedSwapchain = 12L;
		functions.expectedAcquireSemaphore = VK_NULL_HANDLE;
		functions.expectedAcquireFence = functions.availableFences[0];
		functions.nextImageIndex = 2;
		functions.nextAcquireResult = VK_SUCCESS;

		functions.capabilities.currentExtent().set(600, 200);

		// -------------------------acquire 1------------------------------
		// In this scenario, vkQueuePresentKHR will return VK_SUBOPTIMAL_KHR, which means that the current frame
		// finishes, but that the swapchain should be recreated after this frame.

		assertEquals(12L, functions.nextSwapchain);
		assertFalse(functions.availableFences[0].isPending());
		var image1 = swapchains.acquire(VK_PRESENT_MODE_FIFO_KHR, true);
		assertEquals(13L, functions.nextSwapchain);
		assertEquals(12L, image1.swapchain.vkSwapchain);
		assertEquals(2, image1.index);
		assertNotNull(image1.getAcquireSubmission());
		assertThrows(UnsupportedOperationException.class, image1::getAcquireSemaphore);
		assertEquals(600, image1.getWidth());
		assertEquals(200, image1.getHeight());
		assertEquals(createSet(1L), functions.borrowedSemaphores);
		assertEquals(VK_PRESENT_MODE_FIFO_KHR, image1.presentMode);
		assertNull(image1.presentFence);
		assertEquals(1L, image1.getPresentSemaphore());
		assertTrue(functions.availableFences[0].isPending());
		// -------------------------acquire 1------------------------------

		var associatedSwapchain1 = associations.getSwapchainAssociation(image1);
		var associatedImage1 = associations.getImageAssociation(image1);

		// -------------------------present 1------------------------------
		functions.availableFences[0].forceSignal();
		functions.expectedSwitchPresentMode = false;
		functions.nextPresentResult = VK_SUBOPTIMAL_KHR;
		image1.swapchain.presentImage(image1);
		// -------------------------present 1------------------------------

		// -------------------------acquire 2------------------------------
		// The swapchain management system should recreate the swapchain because the previous present result was
		// VK_SUBOPTIMAL_KHR. Since this scenario allows 1 old swapchain, we do NOT call vkDeviceWaitIdle, but use a
		// present fence to figure out when the old swapchain can be destroyed.
		functions.expectedSwapchain = 13L;
		functions.expectedAcquireSemaphore = 2L;
		functions.expectedAcquireFence = null;
		functions.nextImageIndex = 1;
		functions.nextAcquireResult = VK_SUCCESS;
		functions.expectedOldSwapchain = 12L;
		assertEquals(0, functions.deviceWaitIdleCount);
		assertEquals(13L, functions.nextSwapchain);
		var image2 = swapchains.acquire(VK_PRESENT_MODE_FIFO_KHR, false);

		assertEquals(0, functions.deviceWaitIdleCount);
		assertEquals(14L, functions.nextSwapchain);
		assertEquals(13L, image2.swapchain.vkSwapchain);
		assertEquals(1, image2.index);
		assertNull(image2.acquireSubmission);
		assertThrows(UnsupportedOperationException.class, image2::getAcquireSubmission);
		assertEquals(2L, image2.getAcquireSemaphore());
		assertEquals(600, image2.getWidth());
		assertEquals(200, image2.getHeight());

		assertEquals(createSet(1L, 2L, 3L), functions.borrowedSemaphores);
		assertEquals(VK_PRESENT_MODE_FIFO_KHR, image2.presentMode);
		assertSame(functions.availableFences[0], image2.presentFence);
		assertEquals(3L, image2.getPresentSemaphore());
		// -------------------------acquire 2------------------------------

		// -------------------------present 2------------------------------
		functions.expectedSwitchPresentMode = false;
		functions.nextPresentResult = VK_SUCCESS;
		image2.swapchain.presentImage(image2);
		// -------------------------present 2------------------------------

		// -------------------------acquire 3------------------------------
		// The present fence was submitted in the previous frame, but we haven't signalled it yet,
		// so the old swapchain cannot be destroyed yet.
		functions.expectedSwapchain = 13L;
		functions.expectedAcquireSemaphore = 4L;
		functions.expectedAcquireFence = null;
		functions.nextImageIndex = 2;
		functions.nextAcquireResult = VK_SUCCESS;
		assertEquals(0, functions.deviceWaitIdleCount);
		assertEquals(14L, functions.nextSwapchain);
		var image3 = swapchains.acquire(VK_PRESENT_MODE_FIFO_KHR, false);

		assertEquals(0, functions.deviceWaitIdleCount);
		assertEquals(14L, functions.nextSwapchain);
		assertEquals(13L, image3.swapchain.vkSwapchain);
		assertEquals(2, image3.index);
		assertNull(image3.acquireSubmission);
		assertThrows(UnsupportedOperationException.class, image3::getAcquireSubmission);
		assertEquals(4L, image3.getAcquireSemaphore());
		assertEquals(600, image3.getWidth());
		assertEquals(200, image3.getHeight());

		assertEquals(createSet(1L, 2L, 3L, 4L, 5L), functions.borrowedSemaphores);
		assertEquals(VK_PRESENT_MODE_FIFO_KHR, image3.presentMode);
		assertNull(image3.presentFence);
		assertEquals(5L, image3.getPresentSemaphore());
		assertTrue(functions.availableFences[0].isPending());
		// -------------------------acquire 3------------------------------

		var associatedSwapchain3 = associations.getSwapchainAssociation(image3);
		var associatedImage3 = associations.getImageAssociation(image3);

		// Note that we can NOT destroy the old associations before the old swapchain is destroyed
		assertFalse(associatedSwapchain1.wasDestroyed);
		assertFalse(associatedSwapchain1.wasRecycled);
		assertFalse(associatedImage1.wasDestroyed);

		// -------------------------present 3------------------------------
		functions.expectedSwitchPresentMode = false;
		functions.nextPresentResult = VK_ERROR_OUT_OF_DATE_KHR;
		image3.swapchain.presentImage(image3);
		// -------------------------present 3------------------------------

		functions.availableFences[0].forceSignal();

		// -------------------------acquire 4------------------------------
		// We signaled the present fence attached in present 2, so the first swapchain can be destroyed.
		// However, the second swapchain cannot be destroyed yet.
		functions.expectedSwapchain = 14L;
		functions.expectedOldSwapchain = 13L;
		functions.expectedAcquireSemaphore = 6L;
		functions.expectedAcquireFence = null;
		functions.nextImageIndex = 2;
		functions.nextAcquireResult = VK_SUCCESS;
		assertEquals(0, functions.deviceWaitIdleCount);
		assertEquals(14L, functions.nextSwapchain);
		var image4 = swapchains.acquire(VK_PRESENT_MODE_FIFO_KHR, false);

		assertEquals(0, functions.deviceWaitIdleCount);
		assertEquals(15L, functions.nextSwapchain);
		assertEquals(14L, image4.swapchain.vkSwapchain);
		assertEquals(2, image4.index);
		assertNull(image4.acquireSubmission);
		assertThrows(UnsupportedOperationException.class, image4::getAcquireSubmission);
		assertEquals(6L, image4.getAcquireSemaphore());
		assertEquals(600, image4.getWidth());
		assertEquals(200, image4.getHeight());

		assertEquals(createSet(2L, 3L, 4L, 5L, 6L, 7L), functions.borrowedSemaphores);
		assertEquals(VK_PRESENT_MODE_FIFO_KHR, image4.presentMode);
		assertSame(functions.availableFences[0], image4.presentFence);
		assertEquals(7L, image4.getPresentSemaphore());
		// -------------------------acquire 4------------------------------

		// We should have destroyed the first swapchain and the corresponding image attachments,
		// but not the swapchain attachment since we haven't recycled it yet
		assertFalse(associatedSwapchain1.wasDestroyed);
		assertFalse(associatedSwapchain1.wasRecycled);
		assertTrue(associatedImage1.wasDestroyed);
		assertFalse(associatedImage3.wasDestroyed);
		assertEquals(createList(12L), functions.destroyedSwapchains);

		// -------------------------present 4------------------------------
		functions.expectedSwitchPresentMode = false;
		functions.nextPresentResult = VK_ERROR_OUT_OF_DATE_KHR;
		image4.swapchain.presentImage(image4);
		// -------------------------present 4------------------------------

		// -------------------------acquire 5------------------------------
		// Now we have 2 old swapchains, which is too many, so we do vkDeviceWaitIdle to destroy the second one
		functions.expectedSwapchain = 15L;
		functions.expectedOldSwapchain = 14L;
		functions.expectedAcquireSemaphore = 0L;
		functions.expectedAcquireFence = functions.availableFences[0];
		functions.nextImageIndex = 0;
		functions.nextAcquireResult = VK_SUCCESS;
		assertEquals(0, functions.deviceWaitIdleCount);
		assertEquals(15L, functions.nextSwapchain);
		var image5 = swapchains.acquire(VK_PRESENT_MODE_FIFO_KHR, true);

		assertEquals(1, functions.deviceWaitIdleCount);
		assertEquals(16L, functions.nextSwapchain);
		assertEquals(15L, image5.swapchain.vkSwapchain);
		assertEquals(0, image5.index);
		assertNotNull(image5.acquireSubmission);
		assertThrows(UnsupportedOperationException.class, image5::getAcquireSemaphore);
		assertEquals(600, image5.getWidth());
		assertEquals(200, image5.getHeight());

		assertEquals(createSet(2L, 4L, 6L, 7L, 8L), functions.borrowedSemaphores);
		assertEquals(VK_PRESENT_MODE_FIFO_KHR, image5.presentMode);
		assertSame(functions.availableFences[1], image5.presentFence);
		assertEquals(8L, image5.getPresentSemaphore());
		// -------------------------acquire 5------------------------------

		// The first two swapchains are destroyed due to vkDeviceWaitIdle
		// We should have destroyed the old swapchain and the corresponding image attachments,
		// but not the swapchain attachments themselves
		assertFalse(associatedSwapchain1.wasDestroyed);
		assertFalse(associatedSwapchain1.wasRecycled);
		assertFalse(associatedSwapchain3.wasDestroyed);
		assertFalse(associatedSwapchain3.wasRecycled);
		assertTrue(associatedImage1.wasDestroyed);
		assertTrue(associatedImage3.wasDestroyed);
		assertEquals(createList(12L, 13L), functions.destroyedSwapchains);

		// -------------------------present 5------------------------------
		functions.expectedSwitchPresentMode = false;
		functions.nextPresentResult = VK_SUCCESS;
		image5.swapchain.presentImage(image5);
		// -------------------------present 5------------------------------

		swapchains.destroy();
		assertEquals(2, functions.deviceWaitIdleCount);
		assertEquals(createSet(), functions.borrowedSemaphores);
		assertEquals(createList(12L, 13L, 14L, 15L), functions.destroyedSwapchains);
		assertTrue(associatedSwapchain1.wasDestroyed);
		assertFalse(associatedSwapchain1.wasRecycled);
		assertTrue(associatedSwapchain3.wasDestroyed);
		assertFalse(associatedSwapchain3.wasRecycled);
	}
}
