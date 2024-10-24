package com.github.knokko.boiler.buffers;

import com.github.knokko.boiler.BoilerInstance;

/**
 * A helper class that can be used to easily create 1 <i>DeviceVkbBuffer</i> that is shared by multiple non-overlapping
 * <i>VkbBufferRange</i>s. You can use this to, for instance, create multiple vertex buffer ranges that all use
 * different sections of the same <i>VkBuffer</i>. You can use this class by:
 * <ol>
 *     <li>Create an instance using the constructor</li>
 *     <li>Call the <i>add()</i> method for each section you want, and keep the returned <i>Supplier</i>s</li>
 *     <li>Call the <i>build()</i> method</li>
 *     <li>Call the <i>get()</i> methods of the returned <i>Supplier</i>s to get the ranges</li>
 * </ol>
 */
public class SharedDeviceBufferBuilder extends SharedBufferBuilder<DeviceVkbBuffer, VkbBufferRange> {

	public SharedDeviceBufferBuilder(BoilerInstance instance) {
		super(instance);
	}

	@Override
	protected DeviceVkbBuffer buildBuffer(long size, int usage, String name) {
		return instance.buffers.create(size, usage, name);
	}

	@Override
	protected VkbBufferRange createRange(DeviceVkbBuffer buffer, long offset, long size) {
		return new VkbBufferRange(buffer, offset, size);
	}
}
