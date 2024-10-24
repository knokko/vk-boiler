package com.github.knokko.boiler.buffers;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.commands.SingleTimeCommands;
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
		var builder = new SharedDeviceBufferBuilder(instance);
		var getRange0 = builder.add(1, 1234);
		var getRange1 = builder.add(100, 13);
		var getRange2 = builder.add(50, 57);
		var buffer = builder.build(VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, "AlignmentBuffer");

		var range0 = getRange0.get();
		var range1 = getRange1.get();
		var range2 = getRange2.get();

		assertEquals(1, range0.size());
		assertEquals(0, range0.offset());
		assertEquals(100, range1.size());
		assertEquals(13, range1.offset());
		assertEquals(50, range2.size());
		assertEquals(114, range2.offset());
		assertEquals(164, buffer.size());

		buffer.destroy(instance);
	}

	@Test
	public void testBufferCopyShuffle() {
		var sourceBuilder = new SharedMappedBufferBuilder(instance);
		var getSource0 = sourceBuilder.add(7, 4);
		var getSource1 = sourceBuilder.add(8, 8);
		var getSource2 = sourceBuilder.add(1, 1);
		var sourceBuffer = sourceBuilder.build(VK_BUFFER_USAGE_TRANSFER_SRC_BIT, "SharedSource");
		getSource0.get().intBuffer().put(1234);
		getSource1.get().doubleBuffer().put(1.25);
		getSource2.get().byteBuffer().put((byte) 123);

		var middleBuilder = new SharedDeviceBufferBuilder(instance);
		var getMiddle2 = middleBuilder.add(1, 1);
		var getMiddle0 = middleBuilder.add(7, 4);
		var getMiddle1 = middleBuilder.add(8, 8);
		var middleBuffer = middleBuilder.build(
				VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT, "SharedMiddle"
		);

		var destBuilder = new SharedMappedBufferBuilder(instance);
		var getDest1 = destBuilder.add(8, 8);
		var getDest2 = destBuilder.add(1, 1);
		var getDest0 = destBuilder.add(7, 4);
		var destBuffer = destBuilder.build(VK_BUFFER_USAGE_TRANSFER_DST_BIT, "SharedDestination");

		var commands = new SingleTimeCommands(instance);
		commands.submit("SharedShuffle", recorder -> {
			recorder.copyBuffer(getSource0.get().range(), middleBuffer.vkBuffer(), getMiddle0.get().offset());
			recorder.copyBufferRanges(getSource1.get().range(), getMiddle1.get());
			recorder.copyBuffer(getSource2.get().range(), middleBuffer.vkBuffer(), getMiddle2.get().offset());

			recorder.bufferBarrier(getMiddle0.get(), ResourceUsage.TRANSFER_DEST, ResourceUsage.TRANSFER_SOURCE);
			recorder.bufferBarrier(getMiddle1.get(), ResourceUsage.TRANSFER_DEST, ResourceUsage.TRANSFER_SOURCE);
			recorder.bufferBarrier(getMiddle2.get(), ResourceUsage.TRANSFER_DEST, ResourceUsage.TRANSFER_SOURCE);

			recorder.copyBufferRanges(getMiddle0.get(), getDest0.get().range());
			recorder.copyBuffer(getMiddle1.get(), destBuffer.vkBuffer(), getDest1.get().offset());
			recorder.copyBuffer(getMiddle2.get(), destBuffer.vkBuffer(), getDest2.get().offset());
		}).awaitCompletion();
		commands.destroy();

		assertEquals(1234, getDest0.get().intBuffer().get());
		assertEquals(1.25, getDest1.get().doubleBuffer().get());
		assertEquals(123, getDest2.get().byteBuffer().get());

		sourceBuffer.destroy(instance);
		middleBuffer.destroy(instance);
		destBuffer.destroy(instance);
	}

	@Test
	public void testMappedBufferBuilder() {
		var builder = new SharedMappedBufferBuilder(instance);
		var getRange0 = builder.add(3, 0);
		var getRange1 = builder.add(8, 4);

		var buffer = builder.build(VK_BUFFER_USAGE_TRANSFER_DST_BIT, "TestMappedBuffer");
		var range0 = getRange0.get();
		var range1 = getRange1.get();

		assertEquals(0, range0.offset());
		assertEquals(3, range0.size());
		assertEquals(4, range1.offset());
		assertEquals(8, range1.size());
		assertEquals(12, buffer.size());

		range0.byteBuffer().put(2, (byte) 3);
		range1.intBuffer().put(12345);

		assertEquals(3, buffer.fullMappedRange().byteBuffer().get(2));
		assertEquals(12345, buffer.fullMappedRange().intBuffer().get(1));

		buffer.destroy(instance);
	}

	@AfterAll
	public static void cleanUp() {
		instance.destroyInitialObjects();
	}
}
