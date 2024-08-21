package com.github.knokko.boiler.utilities;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.github.knokko.boiler.utilities.CollectionHelper.createSet;
import static com.github.knokko.boiler.utilities.CollectionHelper.decodeStringSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.system.MemoryStack.stackPush;

public class TestCollectionHelper {

	@Test
	public void testStringCoding() {
		Set<Set<String>> testSubjects = CollectionHelper.createSet(
				CollectionHelper.createSet(),
				CollectionHelper.createSet("hello"),
				CollectionHelper.createSet("hello", "world")
		);

		for (Set<String> subject : testSubjects) {
			try (var stack = stackPush()) {
				assertEquals(subject, decodeStringSet(CollectionHelper.encodeStringSet(subject, stack)));
			}
		}
	}

	@Test
	public void testDecodeStringSet() {
		try (var stack = stackPush()) {
			assertTrue(decodeStringSet(null).isEmpty());
			assertTrue(decodeStringSet(stack.callocPointer(0)).isEmpty());

			var ppStrings = stack.pointers(
					stack.UTF8("Let's"),
					stack.UTF8("test"),
					stack.UTF8("decode"),
					stack.UTF8("string"),
					stack.UTF8("set")
			);

			ppStrings.position(2);
			ppStrings.limit(2);
			assertTrue(decodeStringSet(ppStrings).isEmpty());

			ppStrings.limit(3);
			assertEquals(createSet("decode"), decodeStringSet(ppStrings));

			ppStrings.limit(5);
			assertEquals(createSet("decode", "string", "set"), decodeStringSet(ppStrings));

			assertEquals(2, ppStrings.position());
			assertEquals(5, ppStrings.limit());
		}
	}
}
