package com.github.knokko.boiler.utilities;

import org.junit.jupiter.api.Test;

import static com.github.knokko.boiler.utilities.ColorPacker.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestColorPacker {

	@Test
	public void testSimplePackingRGBA() {
		assertEquals(0, rgba(0, 0, 0, 0));
		assertEquals(-1, rgba(255, 255, 255, 255));
		assertEquals(-1, rgba(-1, -1, -1, -1));
		assertEquals(-1, rgba((byte) 255, (byte) 255, (byte) 255, (byte) 255));
		assertEquals(-1, rgba((byte) -1, (byte) -1, (byte) -1, (byte) -1));

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
	}

	@Test
	public void testToString() {
		assertEquals("RGBA(0, 100, 255, 200)", ColorPacker.toString(rgba(0, 100, 255, 200)));
		assertEquals("RGB(255, 100, 0)", ColorPacker.toString(rgb(255, 100, 0)));
	}
}
