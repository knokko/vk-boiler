package com.github.knokko.boiler.buffers;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.commands.SingleTimeCommands;
import com.github.knokko.boiler.memory.MemoryBlockBuilder;
import com.github.knokko.boiler.synchronization.ResourceUsage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.vulkan.VK10.*;

public class TestSharedBufferBuilder {

	private static BoilerInstance instance;

	@BeforeAll
	public static void setUp() {
		instance = new BoilerBuilder(
				VK_API_VERSION_1_0, "TestSharedBufferBuilder", 1
		).validation().forbidValidationErrors().build();
	}

	@Test
	public void testAlignment() {
		var builder = new MemoryBlockBuilder(instance, "Memory");
		var buffer0 = builder.addBuffer(1, 1234, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
		var buffer1 = builder.addBuffer(100, 13, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
		var buffer2 = builder.addBuffer(50, 57, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
		var memory = builder.allocate(false);

		assertEquals(1, buffer0.size);
		assertEquals(0, buffer0.offset);
		assertEquals(100, buffer1.size);
		assertEquals(13, buffer1.offset);
		assertEquals(50, buffer2.size);
		assertEquals(114, buffer2.offset);

		memory.free(instance);
	}

	@Test
	public void testBufferCopyShuffle() {
		var builder = new MemoryBlockBuilder(instance, "Memory");
		var source0 = builder.addMappedBuffer(7, 4, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
		var source1 = builder.addMappedBuffer(8, 8, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
		var source2 = builder.addMappedBuffer(1, 1, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);

		var middle2 = builder.addBuffer(1, 1, VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT);
		var middle0 = builder.addBuffer(7, 4, VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT);
		var middle1 = builder.addBuffer(8, 8, VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT);

		var destination1 = builder.addMappedBuffer(8, 8, VK_BUFFER_USAGE_TRANSFER_DST_BIT);
		var destination2 = builder.addMappedBuffer(1, 1, VK_BUFFER_USAGE_TRANSFER_DST_BIT);
		var destination0 = builder.addMappedBuffer(7, 4, VK_BUFFER_USAGE_TRANSFER_DST_BIT);
		var memory = builder.allocate(false);

		source0.intBuffer().put(1234);
		source1.doubleBuffer().put(1.25);
		source2.byteBuffer().put((byte) 123);

		SingleTimeCommands.submit(instance, "SharedShuffle", recorder -> {
			recorder.bulkCopyBuffers(new VkbBuffer[] { source0, source1, source2 }, new VkbBuffer[] { middle0, middle1, middle2 });
			recorder.bulkBufferBarrier(ResourceUsage.TRANSFER_DEST, ResourceUsage.TRANSFER_SOURCE, middle0, middle1, middle2);
			recorder.bulkCopyBuffers(new VkbBuffer[] { middle0, middle1, middle2 }, new VkbBuffer[] { destination0, destination1, destination2 });
		}).destroy();

		assertEquals(1234, destination0.intBuffer().get());
		assertEquals(1.25, destination1.doubleBuffer().get());
		assertEquals(123, destination2.byteBuffer().get());

		memory.free(instance);
	}

	@Test
	public void testMappedBufferBuilder() {
		var builder = new MemoryBlockBuilder(instance, "Memory");
		var buffer0 = builder.addMappedBuffer(3, 1, VK_BUFFER_USAGE_TRANSFER_DST_BIT);
		var buffer1 = builder.addMappedBuffer(8, 4, VK_BUFFER_USAGE_TRANSFER_DST_BIT);
		var memory = builder.allocate(false);

		assertEquals(0, buffer0.offset);
		assertEquals(3, buffer0.size);
		assertEquals(4, buffer1.offset);
		assertEquals(8, buffer1.size);

		buffer0.byteBuffer().put(2, (byte) 3);
		buffer1.intBuffer().put(12345);

		assertEquals(3, buffer0.byteBuffer().get(2));
		assertEquals(12345, buffer1.intBuffer().get());

		memory.free(instance);
	}

	@AfterAll
	public static void cleanUp() {
		instance.destroyInitialObjects();
	}
}
