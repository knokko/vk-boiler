package com.github.knokko.boiler.utilities;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.lwjgl.system.MemoryUtil.memUTF8;

public class CollectionHelper {

	/**
	 * Creates a <i>Set</i> containing the given elements
	 */
	@SafeVarargs
	public static <T> Set<T> createSet(T... elements) {
		var result = new HashSet<T>();
		Collections.addAll(result, elements);
		return result;
	}

	/**
	 * Decodes a <i>PointerBuffer</i> containing pointers to C-style strings into a Java <i>String Set</i>.
	 * This can be used to parse <i>ppEnabledExtensionNames</i>.
	 */
	public static Set<String> decodeStringSet(PointerBuffer ppStrings) {
		if (ppStrings == null) return new HashSet<>();
		Set<String> result = new HashSet<>(ppStrings.remaining());
		for (int index = ppStrings.position(); index < ppStrings.limit(); index++) {
			result.add(memUTF8(ppStrings.get(index)));
		}
		return result;
	}

	/**
	 * Encodes a Java <i>String Set</i> into a <i>PointerBuffer</i> containing pointers to C-style strings. This can
	 * be used to populate <i>ppEnabledExtensionNames</i>.
	 */
	public static PointerBuffer encodeStringSet(Set<String> strings, MemoryStack stack) {
		if (strings.isEmpty()) return null;
		PointerBuffer result = stack.callocPointer(strings.size());
		for (var element : strings) {
			result.put(stack.UTF8(element));
		}
		result.flip();
		return result;
	}
}
