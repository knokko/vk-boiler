package com.github.knokko.boiler.sync;

import com.github.knokko.boiler.builder.BoilerBuilder;
import com.github.knokko.boiler.commands.CommandRecorder;
import com.github.knokko.boiler.BoilerInstance;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class TestFenceBank {

	private void signalFence(BoilerInstance instance, VkbFence fence) {
		var commandPool = instance.commands.createPool(
				0, instance.queueFamilies().graphics().index(), "SignalFence"
		);
		var commandBuffer = instance.commands.createPrimaryBuffers(commandPool, 1, "Signal")[0];
		try (var stack = stackPush()) {
			var commands = CommandRecorder.begin(commandBuffer, instance, stack, "Signal");
			commands.end();

			instance.queueFamilies().graphics().first().submit(
					commandBuffer, "Signal", null, fence
			);
			fence.awaitSignal();
		}

		vkDestroyCommandPool(instance.vkDevice(), commandPool, null);
	}

	@Test
	public void complexFenceBankTest() {
		var instance = new BoilerBuilder(VK_API_VERSION_1_0, "TestFenceBank", 1)
				.validation()
				.forbidValidationErrors()
				.build();

		var fence1 = instance.sync.fenceBank.borrowFence(false, "Fence1");
		assertFalse(fence1.isPending());
		assertFalse(fence1.isSignaled());
		signalFence(instance, fence1);
		assertFalse(fence1.isPending());
		assertTrue(fence1.isSignaled());

		var fence2 = instance.sync.fenceBank.borrowFence(false, "Fence2");
		assertFalse(fence2.isSignaled());
		assertFalse(fence2.isPending());

		instance.sync.fenceBank.returnFence(fence1);
		assertSame(fence1, instance.sync.fenceBank.borrowFence(false, "Fence3"));
		assertFalse(fence1.isPending());
		assertFalse(fence1.isSignaled());

		instance.sync.fenceBank.returnFence(fence2);
		assertSame(fence2, instance.sync.fenceBank.borrowFence(false, "Fence4"));
		assertFalse(fence2.isSignaled());
		assertFalse(fence2.isPending());

		instance.sync.fenceBank.returnFence(fence1);
		instance.sync.fenceBank.returnFence(fence2);
		instance.destroyInitialObjects();
	}

	static boolean contains(VkbFence[] array, VkbFence target) {
		return Arrays.stream(array).anyMatch(candidate -> candidate == target);
	}

	@Test
	public void testBulkActions() {
		var instance = new BoilerBuilder(VK_API_VERSION_1_0, "TestFenceBankBulk", 1)
				.validation()
				.forbidValidationErrors()
				.build();

		var bank = instance.sync.fenceBank;

		var fences = bank.borrowFences(10, false, "OldFence");

		assertFalse(fences[5].isPending());
		assertFalse(fences[5].isSignaled());
		signalFence(instance, fences[3]);
		assertTrue(fences[3].isSignaled());
		assertFalse(fences[3].isPending());

		bank.returnFences(fences[3], fences[5]);

		var newFences = bank.borrowFences(3, false, "NewFence");
		assertTrue(contains(newFences, fences[3]));
		assertTrue(contains(newFences, fences[5]));
		assertFalse(contains(newFences, fences[4]));
		for (var fence : newFences) {
			assertFalse(fence.isPending());
			assertFalse(fence.isSignaled());
		}

		bank.returnFences(fences[0], fences[1], fences[2], fences[4]);
		bank.returnFences(newFences);
		bank.returnFences(Arrays.copyOfRange(fences, 6, 10));

		instance.destroyInitialObjects();
	}

	@Test
	public void testBorrowSignaled() {
		var instance = new BoilerBuilder(VK_API_VERSION_1_0, "TestFenceBankBulk", 1)
				.validation()
				.forbidValidationErrors()
				.build();
		var bank = instance.sync.fenceBank;

		VkbFence[] fences = bank.borrowFences(5, true, "OldFence");
		assertEquals(5, fences.length);
		for (var fence : fences) {
			assertFalse(fence.isPending());
			assertTrue(fence.isSignaled());
		}

		bank.returnFences(fences[0], fences[1], fences[2]);

		VkbFence[] newFences = bank.borrowFences(5, true, "NewFence");
		assertTrue(contains(newFences, fences[0]));
		assertFalse(contains(newFences, fences[3]));
		assertTrue(contains(fences, newFences[0]));
		assertFalse(contains(fences, newFences[4]));

		for (var fence : newFences) {
			assertTrue(fence.isSignaled());
			assertFalse(fence.isPending());
		}
		bank.returnFences(newFences);
		bank.returnFences(fences[3], fences[4]);

		instance.destroyInitialObjects();
	}
}
