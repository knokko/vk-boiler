package com.github.knokko.boiler.buffers;

import com.github.knokko.boiler.BoilerInstance;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import static com.github.knokko.boiler.utilities.BoilerMath.nextMultipleOf;

/**
 * The parent class of <i>SharedDeviceBufferBuilder</i> and <i>SharedMappedBufferBuilder</i>
 * @param <B> <i>DeviceVkbBuffer</i> or <i>MappedVkbBuffer</i>
 * @param <R> <i>VkbBufferRange</i> or <i>MappedVkbBufferRange</i>
 */
public abstract class SharedBufferBuilder<B extends VkbBuffer, R> {

	protected final BoilerInstance instance;
	protected final Set<Long> alignments = new HashSet<>();
	protected long totalSize = 0L;
	protected B buffer;

	public SharedBufferBuilder(BoilerInstance instance) {
		this.instance = instance;
	}

	/**
	 * Adds a section with the given size and alignment, to the to-be-built buffer. This method must be called
	 * <b>before</b> <i>build()</i>.
	 * @param size The size of the section, in bytes
	 * @param alignment The alignment of the section, in bytes. Use 0 or 1 if there is no alignment requirement.
	 * @return a <i>Supplier</i> whose <i>get()</i> method will return the assigned buffer range. Note that you must
	 * <b>not</b> call <i>get()</i> until you have called <i>build()</i>
	 */
	public Supplier<R> add(long size, long alignment) {
		if (buffer != null) throw new IllegalStateException("Buffer has already been built");
		if (alignment > 0L) {
			totalSize = nextMultipleOf(totalSize, alignment);
			alignments.add(alignment);
		}
		long offset = totalSize;
		totalSize += size;

		return () -> {
			if (buffer == null) throw new IllegalStateException("Buffer hasn't been built yet");
			return createRange(buffer, offset, size);
		};
	}

	/**
	 * Builds the buffer. Note that you must call the <i>add()</i> method (usually multiple times) <i>before</i>
	 * calling this method. After calling this method, you can use the <i>get()</i> method of all the <i>Supplier</i>s
	 * returned by the <i>add()</i> method.
	 * @param usage The <i>VkBufferUsage</i> flags of the buffer to be built
	 * @param name The debug name of the buffer to be built, which will be ignored when validation is not enabled
	 * @return The created <i>VkbBuffer</i>. You don't necessarily need this return value since you can also get it
	 * from the buffer ranges.
	 */
	public B build(int usage, String name) {
		if (buffer != null) throw new IllegalStateException("Buffer has already been built");
		this.buffer = buildBuffer(totalSize, usage, name);
		return this.buffer;
	}

	/**
	 * Modify this set is at your own risk!
	 * @return a set containing the alignments of all sections
	 */
	public Set<Long> getAlignments() {
		return alignments;
	}

	protected abstract B buildBuffer(long size, int usage, String name);

	protected abstract R createRange(B buffer, long offset, long size);
}
