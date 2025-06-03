package com.github.knokko.boiler.buffers;

import com.github.knokko.boiler.exceptions.PerFrameOverflowException;

import java.util.HashMap;
import java.util.Map;

import static com.github.knokko.boiler.utilities.BoilerMath.nextMultipleOf;

/**
 * Utility class to facilitate the sharing of one-time-use per-frame buffer storage between different renderers.
 *
 * <p>
 *     To use this class, you should create 1 instance (per rendering thread) of this class, which should usually be
 *     reused until the application is closed.
 * </p>
 *
 * <p>
 *     At the start of this frame, you should call the {@link #startFrame(int)} method, where
 *     {@code frameIndex = frameCounter % numberOfFramesInFlight}, and {@code frameCounter} is a variable that is
 *     incremented once per frame.
 * </p>
 *
 * <p>
 *     Whenever a renderer needs some one-time-use per-frame data, it should call the {@link #allocate(long, long)}
 *     method to request a piece of data.
 * </p>
 */
public class PerFrameBuffer {

	/**
	 * The buffer/memory range that will be used by this per-frame buffer. Pieces of this range will be returned by
	 * the {@link #allocate(long, long)} method.
	 */
	public final MappedVkbBuffer buffer;

	private final Map<Integer, Long> limits = new HashMap<>();

	private long currentOffset, currentLimit;

	/**
	 * Constructs a new per-frame buffer, which you should usually do only once.
	 * @param buffer See {@link #buffer}
	 */
	public PerFrameBuffer(MappedVkbBuffer buffer) {
		this.buffer = buffer;
	}

	/**
	 * You should call this at the start of each frame. When you:
	 * <ol>
	 *     <li>Call {@code startFrame(i)}</li>
	 *     <li>Call {@link #allocate(long, long)} a couple of times</li>
	 *     <li>Call {@code startFrame(j)}</li>
	 * </ol>
	 * All space used for the allocations between frame-in-flight {@code i} and frame-in-flight {@code j} can be reused
	 * after you call {@code startFrame(i)} again.
	 * @param frameIndex {@code frameCounter % numberOfFramesInFlight}, when you start your {@code frameCounter}th
	 *                                                                frame.
	 */
	public void startFrame(int frameIndex) {
		limits.remove(frameIndex);

		long nextLimit = Long.MAX_VALUE;
		for (long candidate : limits.values()) {
			if (candidate >= currentOffset && candidate < nextLimit) nextLimit = candidate;
		}

		if (nextLimit == Long.MAX_VALUE) {
			for (long candidate : limits.values()) {
				if (candidate < nextLimit) nextLimit = candidate;
			}
		}

		if (nextLimit == Long.MAX_VALUE) {
			nextLimit = currentOffset - 1;
		}

		if (nextLimit < 0) nextLimit = buffer.size;

		currentLimit = nextLimit;
		limits.put(frameIndex, currentOffset - 1);
	}

	private void align(long alignment) {
		long fullOffset = nextMultipleOf(buffer.offset + currentOffset, alignment);
		currentOffset = fullOffset - buffer.offset;
	}

	/**
	 * Allocates {@code byteSize} bytes of memory, ensures that the {@link MappedVkbBuffer#offset} of the
	 * returned buffer range will be a multiple of {@code alignment}
	 * @param byteSize The size of the memory to claim, in bytes
	 * @param alignment The alignment of the memory to claim, in bytes
	 * @return The allocated buffer range
	 */
	public MappedVkbBuffer allocate(long byteSize, long alignment) {
		align(alignment);
		long nextOffset = currentOffset + byteSize;

		if (currentOffset > currentLimit && nextOffset > buffer.size) {
			currentOffset = 0L;
			align(alignment);
			if (currentOffset >= currentLimit) {
				throw new PerFrameOverflowException("PerFrameBuffer overflow case 2: byteSize is " + byteSize);
			}
			nextOffset = currentOffset + byteSize;
		}

		if (currentOffset <= currentLimit && nextOffset > currentLimit) {
			throw new PerFrameOverflowException("PerFrameBuffer overflow case 1: byteSize is " + byteSize);
		}

		var result = buffer.child(currentOffset, byteSize);
		currentOffset = nextOffset;
		return result;
	}
}
