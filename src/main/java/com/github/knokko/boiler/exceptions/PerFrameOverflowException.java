package com.github.knokko.boiler.exceptions;

/**
 * This exception will be thrown when {@link com.github.knokko.boiler.buffers.PerFrameBuffer#allocate(long, long)}
 * fails because there is not enough space in the per-frame buffer. This can basically have 2 possible causes:
 * <ul>
 *     <li>The per-frame buffer is too small</li>
 *     <li>
 *         You forgot to call {@link com.github.knokko.boiler.buffers.PerFrameBuffer#startFrame(int)},
 *         preventing it from reclaiming memory from previous frames.
 *     </li>
 * </ul>
 */
public class PerFrameOverflowException extends RuntimeException {

	public PerFrameOverflowException(String message) {
		super(message);
	}
}
