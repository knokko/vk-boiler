package com.github.knokko.boiler.buffers;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.memory.callbacks.CallbackUserData;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferViewCreateInfo;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.lwjgl.vulkan.VK10.*;

/**
 * A {@link VkbBuffer} represents a segment of a <b>VkBuffer</b>. Every initialized instance claims bytes
 * {@code offset} to {@code offset + size} of {@code vkBuffer}. {@link com.github.knokko.boiler.memory.MemoryCombiner}
 * is the recommended way to create {@link VkbBuffer}s.
 */
public class VkbBuffer {

	/**
	 * The <b>VkBuffer</b>, or <b>VK_NULL_HANDLE</b> when not yet initialized
	 */
	public long vkBuffer;

	/**
	 * The offset (in bytes) of this {@link VkbBuffer} into the <b>VkBuffer</b>. When this is not zero, the first byte
	 * of the <b>VkBuffer</b> is claimed by another {@link VkbBuffer}.
	 */
	public long offset;

	/**
	 * The size of this {@link VkbBuffer}, in bytes. Note that {@code vkBuffer} can be larger.
	 */
	public final long size;

	/**
	 * The memory type index of the memory allocation to which this buffer was bound. This field should be set during
	 * {@link com.github.knokko.boiler.memory.MemoryCombiner#build}.
	 */
	public int memoryTypeIndex;

	public VkbBuffer(long vkBuffer, long offset, long size) {
		this.vkBuffer = vkBuffer;
		this.offset = offset;
		this.size = size;
	}

	/**
	 * Checks whether this buffer resides in device-local memory. This method is potentially useful if this buffer
	 * was created using {@link com.github.knokko.boiler.memory.MemoryCombiner#addMappedDeviceLocalBuffer} to check
	 * whether the buffer was really allocated in device-local memory.
	 * @param instance The {@link BoilerInstance}
	 * @return True if and only if the buffer resides in a device-local memory type
	 */
	public boolean isDeviceLocal(BoilerInstance instance) {
		return instance.memoryInfo.deviceLocalMemoryTypes.contains(memoryTypeIndex);
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof VkbBuffer buffer) {
			return vkBuffer == buffer.vkBuffer && offset == buffer.offset && size == buffer.size;
		} else return false;
	}

	@Override
	public int hashCode() {
		return (int) vkBuffer + 13 * (int) offset - 31 * (int) size;
	}

	/**
	 * Creates an uninitialized {@link VkbBuffer} that will have the given size (in bytes)
	 */
	public VkbBuffer(long size) {
		this(VK_NULL_HANDLE, -1, size);
	}

	protected void validateChildRange(long childOffset, long childSize) {
		if (childOffset + childSize > size) throw new IllegalArgumentException(
				"Child (offset=" + childOffset + ", size=" + childSize + ") out of range for parent (offset="
						+ offset + ", size=" + size + ")"
		);
	}

	/**
	 * Creates a 'child' buffer of this buffer, from {@code this.offset + childOffset} to
	 * {@code this.offset + childOffset + childSize}
	 * @param childOffset The starting offset of the child buffer (in bytes), relative to {@code this.offset}
	 * @param childSize The size of the child buffer, in bytes
	 * @return The child buffer
	 */
	public VkbBuffer child(long childOffset, long childSize) {
		validateChildRange(childOffset, childSize);
		return new VkbBuffer(vkBuffer, offset + childOffset, childSize);
	}

	/**
	 * Creates a <b>VkBufferView</b> of this buffer segment.
	 * @param format The buffer format, which is passed to {@link VkBufferViewCreateInfo#format(int)}
	 * @param instance The {@link BoilerInstance} (this parameter is needed to get e.g. the <b>VkDevice</b>)
	 * @param debugName The debug name (only used when validation and debug utils are enabled)
	 * @return The <b>VkBufferView</b>
	 */
	public long createView(int format, BoilerInstance instance, String debugName) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			var ciView = VkBufferViewCreateInfo.calloc(stack);
			ciView.sType$Default();
			ciView.format(format);
			ciView.buffer(vkBuffer);
			ciView.offset(offset);
			ciView.range(size);

			var pView = stack.callocLong(1);
			assertVkSuccess(vkCreateBufferView(
					instance.vkDevice(), ciView, CallbackUserData.BUFFER_VIEW.put(stack, instance), pView
			), "CreateBufferView", debugName);

			long view = pView.get(0);
			instance.debug.name(stack, view, VK_OBJECT_TYPE_BUFFER_VIEW, debugName);
			return view;
		}
	}
}
