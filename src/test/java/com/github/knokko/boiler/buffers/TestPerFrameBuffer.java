package com.github.knokko.boiler.buffers;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.exceptions.PerFrameOverflowException;
import com.github.knokko.boiler.memory.MemoryBlockBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.lwjgl.vulkan.VK10.VK_API_VERSION_1_0;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestPerFrameBuffer {

	private final BoilerInstance instance = new BoilerBuilder(
			VK_API_VERSION_1_0, "TestPerFrameBuffer", 1
	).validation().forbidValidationErrors().build();

	@Test
	public void testBasic() {
		var builder = new MemoryBlockBuilder(instance, "Memory");
		var buffer = builder.addMappedBuffer(10, 1, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
		var memory = builder.allocate(false);

		var perFrame = new PerFrameBuffer(buffer);

		perFrame.startFrame(0);
		assertEquals(0L, perFrame.allocate(2, 3).offset);
		assertEquals(2L, perFrame.allocate(2, 2).offset);
		assertEquals(5L, perFrame.allocate(1, 5).offset);

		perFrame.startFrame(1);
		assertEquals(6L, perFrame.allocate(3, 3).offset);
		assertEquals(9L, perFrame.allocate(1, 3).offset);

		perFrame.startFrame(0);
		assertEquals(0L, perFrame.allocate(3, 1).offset);

		memory.free(instance);
	}

	@Test
	public void testOverflowFirstFrame() {
		var builder = new MemoryBlockBuilder(instance, "Memory");
		var buffer = builder.addMappedBuffer(10, 1, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
		var memory = builder.allocate(true);

		var perFrame = new PerFrameBuffer(buffer);

		perFrame.startFrame(0);
		assertEquals(0L, perFrame.allocate(6, 3).offset);
		assertThrows(PerFrameOverflowException.class, () -> perFrame.allocate(6, 1));

		memory.free(instance);
	}

	@Test
	public void testOverflowSecondFrame() {
		var builder = new MemoryBlockBuilder(instance, "Memory");
		var buffer = builder.addMappedBuffer(10, 1, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
		var memory = builder.allocate(false);
		var perFrame = new PerFrameBuffer(buffer);

		perFrame.startFrame(0);
		assertEquals(0L, perFrame.allocate(6, 3).offset);
		perFrame.startFrame(1);
		assertThrows(PerFrameOverflowException.class, () -> perFrame.allocate(6, 1));

		memory.free(instance);
	}

	@Test
	public void testRespectAlignmentWithRangeOffset() {
		var builder = new MemoryBlockBuilder(instance, "Memory");
		var buffer = builder.addMappedBuffer(15, 1, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
		var memory = builder.allocate(true);
		var perFrame = new PerFrameBuffer(buffer.child(3, 8));

		perFrame.startFrame(1);
		assertEquals(5L, perFrame.allocate(3, 5).offset);
		assertEquals(8L, perFrame.allocate(2, 4).offset);

		perFrame.startFrame(1);
		assertEquals(5L, perFrame.allocate(3, 5).offset);

		memory.free(instance);
	}

	@Test
	public void testWrapAlignmentOverflow1() {
		var builder = new MemoryBlockBuilder(instance, "Memory");
		var buffer = builder.addMappedBuffer(15, 1, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
		var memory = builder.allocate(true);
		var perFrame = new PerFrameBuffer(buffer.child(1, 10));

		perFrame.startFrame(0);
		assertEquals(1L, perFrame.allocate(3, 1).offset);

		perFrame.startFrame(1);
		assertEquals(5L, perFrame.allocate(3, 5).offset);
		assertThrows(PerFrameOverflowException.class, () -> perFrame.allocate(3, 5));

		memory.free(instance);
	}

	@Test
	public void testWrapAlignmentOverflow2() {
		var builder = new MemoryBlockBuilder(instance, "Memory");
		var buffer = builder.addMappedBuffer(15, 1, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
		var memory = builder.allocate(false);
		var perFrame = new PerFrameBuffer(buffer.child(1, 10));

		perFrame.startFrame(0);
		assertEquals(1L, perFrame.allocate(2, 1).offset);

		perFrame.startFrame(1);
		assertEquals(3L, perFrame.allocate(2, 1).offset);

		perFrame.startFrame(0);
		assertEquals(5L, perFrame.allocate(3, 1).offset);

		perFrame.startFrame(1);
		assertThrows(PerFrameOverflowException.class, () -> perFrame.allocate(5, 5));

		memory.free(instance);
	}

	@AfterAll
	public void tearDown() {
		instance.destroyInitialObjects();
	}
}
