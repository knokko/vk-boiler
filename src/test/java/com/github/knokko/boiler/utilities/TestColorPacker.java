package com.github.knokko.boiler.utilities;

import org.junit.jupiter.api.Test;

import static com.github.knokko.boiler.utilities.ColorPacker.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestColorPacker {

	@Test
	public void testSimplePackingRGBA() {
		assertEquals(0, rgba(0, 0, 0, 0));
		assertEquals(0, rgba(0f, 0f, 0f, 0f));
		assertEquals(-1, rgba(255, 255, 255, 255));
		assertEquals(-1, rgba(-1, -1, -1, -1));
		assertEquals(-1, rgba((byte) 255, (byte) 255, (byte) 255, (byte) 255));
		assertEquals(-1, rgba((byte) -1, (byte) -1, (byte) -1, (byte) -1));
		assertEquals(-1, rgba(1f, 1f, 1f, 1f));

		int high = rgba((byte) 255, (byte) 254, (byte) 253, (byte) 252);
		assertEquals(high, rgba(255, 254, 253, 252));
		assertEquals(high, rgba(-1, -2, -3, -4));
		assertEquals((byte) 255, red(high));
		assertEquals((byte) 254, green(high));
		assertEquals((byte) 253, blue(high));
		assertEquals((byte) 252, alpha(high));
		assertEquals(255, unsigned(red(high)));
		assertEquals(254, unsigned(green(high)));
		assertEquals(253, unsigned(blue(high)));
		assertEquals(252, unsigned(alpha(high)));
		assertEquals(1f, normalize(red(high)));

		int low = rgba((byte) 1, (byte) 2, (byte) 3, (byte) 4);
		assertEquals(low, rgba(1, 2, 3, 4));
		assertEquals(1, red(low));
		assertEquals(2, green(low));
		assertEquals(3, blue(low));
		assertEquals(4, alpha(low));
		assertEquals(1, unsigned(red(low)));
		assertEquals(2, unsigned(green(low)));
		assertEquals(3, unsigned(blue(low)));
		assertEquals(4, unsigned(alpha(low)));
	}

	@Test
	public void testSimplePackingRGB() {
		assertEquals(rgba(0, 0, 0, 255), rgb(0, 0, 0));
		assertEquals(rgba(0, 0, 0, 255), rgb((byte) 0, (byte) 0, (byte) 0));
		assertEquals(-1, rgb(255, -1, 255));
		assertEquals(-1, rgb((byte) -1, (byte) 255, (byte) -1));
		assertEquals(rgba(250, 251, 252, 255), rgb(250, 251, 252));
		assertEquals(rgba(250, 251, 252, 255), rgb((byte) 250, (byte) 251, (byte) 252));
		assertEquals(rgba(250, 251, 252, 255), rgb(-6, -5, -4));
		assertEquals(rgba(1, 2, 3, 255), rgb(1, 2, 3));
		assertEquals(rgba(1, 2, 3, 255), rgb((byte) 1, (byte) 2, (byte) 3));
		assertEquals(rgba(0, 127, 128, 255), rgb(0f, 0.499f, 0.501f));
	}

	@Test
	public void testToString() {
		assertEquals("RGBA(0, 100, 255, 200)", ColorPacker.toString(rgba(0, 100, 255, 200)));
		assertEquals("RGB(255, 100, 0)", ColorPacker.toString(rgb(255, 100, 0)));
	}

	@Test
	public void testNormalize() {
		for (byte b = Byte.MIN_VALUE; b < Byte.MAX_VALUE; b++) {
			assertEquals(b, denormalize(normalize(b)));
			assertEquals(b, denormalize(normalize(b) - 0.4f / 255f));
			assertEquals(b, denormalize(normalize(b) + 0.4f / 255f));
		}
		assertEquals(Byte.MAX_VALUE, denormalize(normalize(Byte.MAX_VALUE)));
	}

	@Test
	public void testSrgbToLinear() {
		assertEquals(0f, linearToSrgb(0f), 0.001f);
		assertEquals(0f, srgbToLinear(0f), 0.001f);
		assertEquals(1f, linearToSrgb(1f), 0.001f);
		assertEquals(1f, srgbToLinear(1f), 0.001f);

		assertEquals(-1, srgbToLinear(-1));
		assertEquals(-1, linearToSrgb(-1));
		assertEquals(0, srgbToLinear(0));
		assertEquals(0, linearToSrgb(0));

		float[] testValues = { 0f, 0.1f, 0.003f, 0.0033f, 0.039f, 0.041f, 0.4f, 0.8f, 1f };
		for (float test : testValues) {
			assertEquals(test, srgbToLinear(linearToSrgb(test)), 0.001f);
			assertEquals(test, linearToSrgb(srgbToLinear(test)), 0.001f);
		}

		int testCase = srgbToLinear(rgba(242, 183, 113, 123));
		assertEquals(226, unsigned(red(testCase)));
		assertEquals(121, unsigned(green(testCase)));
		assertEquals(42, unsigned(blue(testCase)));
		assertEquals(123, unsigned(alpha(testCase)));
	}

	@Test
	public void testChangeAlpha() {
		int original = rgba(0, 100, 255, 200);

		assertEquals(rgba(0, 100, 255, 0), changeAlpha(original, (byte) 0));
		assertEquals(rgba(0, 100, 255, 0), changeAlpha(original, 0));
		assertEquals(rgba(0, 100, 255, 0), changeAlpha(original, 0f));

		assertEquals(rgba(0, 100, 255, 255), changeAlpha(original, (byte) -1));
		assertEquals(rgba(0, 100, 255, 255), changeAlpha(original, 255));
		assertEquals(rgba(0, 100, 255, 255), changeAlpha(original, 1f));

		assertEquals(rgba(0, 100, 255, 128), changeAlpha(original, (byte) -128));
		assertEquals(rgba(0, 100, 255, 128), changeAlpha(original, 128));
		assertEquals(rgba(0, 100, 255, 128), changeAlpha(original, 0.501f));
	}

	@Test
	public void testMultiplyAlphaFromOpaque() {
		int original = rgb(0, 100, 255);
		assertEquals(rgba(0, 100, 255, 0), multiplyAlpha(original, 0f));
		assertEquals(rgba(0, 100, 255, 255), multiplyAlpha(original, 1f));
		assertEquals(rgba(0, 100, 255, 128), multiplyAlpha(original, 0.501f));
	}

	@Test
	public void testMultiplyAlphaFromTranslucent() {
		int original = rgba(0, 100, 255, 200);
		assertEquals(rgba(0, 100, 255, 0), multiplyAlpha(original, 0f));
		assertEquals(rgba(0, 100, 255, 200), multiplyAlpha(original, 1f));
		assertEquals(rgba(0, 100, 255, 100), multiplyAlpha(original, 0.5f));
	}

	@Test
	public void testMultiplyColors() {
		assertEquals(
				rgba(0.36f, 0.42f, 0.72f, 0.9f), multiplyColors(
						rgba(0.4f, 0.6f, 0.9f, 0.9f),
						rgba(0.9f, 0.7f, 0.8f, 1f)
				)
		);
	}
}
