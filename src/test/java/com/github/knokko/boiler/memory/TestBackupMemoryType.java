package com.github.knokko.boiler.memory;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.buffers.MappedVkbBuffer;
import com.github.knokko.boiler.builders.BoilerBuilder;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.vulkan.VK10.VK_API_VERSION_1_0;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;

public class TestBackupMemoryType {

	// This is not a proper unit test because it relies too much on my hardware.
	// It is simply a convenient way to manually verify whether back-up memory types work.
	public static void main(String[] args) {
		var instance = new BoilerBuilder(
				VK_API_VERSION_1_0, "TestBackupMemoryType", 1
		).validation().forbidValidationErrors().build();

		test(instance, false, false);
		test(instance, true, false);
		test(instance, false, true);

		instance.destroyInitialObjects();
	}

	private static void test(BoilerInstance instance, boolean useVma, boolean recycle) {
		var combiner = new MemoryCombiner(instance, "TooMuch?");
		var largeLowPriority = combiner.addMappedDeviceLocalBuffer(
				1_000_000_000L, 1L, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, 0f
		);
		var largeHighPriority = combiner.addMappedDeviceLocalBuffer(
				1_000_000_000L, 1L, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, 1f
		);
		var smallMediumPriority = new MappedVkbBuffer[50];
		var smallLowPriority = new MappedVkbBuffer[smallMediumPriority.length];
		for (int index = 0; index < smallMediumPriority.length; index++) {
			smallMediumPriority[index] = combiner.addMappedDeviceLocalBuffer(
					10_000_000L, 1L, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, 0.5f + 0.001f * index
			);
			smallLowPriority[index] = combiner.addMappedDeviceLocalBuffer(
					10_000_000L, 1L, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, 0.25f + 0.001f * index
			);
		}

		MemoryBlock memory;
		if (recycle) {
			var oldCombiner = new MemoryCombiner(instance, "Old");
			oldCombiner.addMappedDeviceLocalBuffer(
					100L, 1, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, 0.5f
			);
			var oldMemory = oldCombiner.build(false);
			memory = combiner.buildAndRecycle(oldMemory);
		} else memory = combiner.build(useVma);

		assertFalse(largeLowPriority.isDeviceLocal(instance));
		assertFalse(largeHighPriority.isDeviceLocal(instance));

		long mediumDeviceLocal = Arrays.stream(smallMediumPriority).filter(
				buffer -> buffer.isDeviceLocal(instance)
		).count();
		long lowDeviceLocal = Arrays.stream(smallLowPriority).filter(
				buffer -> buffer.isDeviceLocal(instance)
		).count();
		assertEquals(0, lowDeviceLocal);
		System.out.println("medium device local is " + mediumDeviceLocal);
		assertTrue(mediumDeviceLocal > 0);
		assertTrue(mediumDeviceLocal < smallMediumPriority.length);

		memory.destroy(instance);
	}
}
