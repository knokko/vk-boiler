package com.github.knokko.boiler.exceptions;

import static org.lwjgl.sdl.SDLError.SDL_GetError;

/**
 * This exception will be thrown by <i>BoilerBuilder</i> and <i>WindowBuilder</i> when SDL functions fail (return false)
 */
public class SDLFailureException extends RuntimeException {

	public static void assertSdlSuccess(boolean result, String functionName) {
		if (result) return;
		throw new SDLFailureException("SDL_" + functionName + " failed with error " + SDL_GetError());
	}

	public SDLFailureException(String message) {
		super(message);
	}
}
