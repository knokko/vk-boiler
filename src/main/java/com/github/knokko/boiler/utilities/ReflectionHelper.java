package com.github.knokko.boiler.utilities;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;

public class ReflectionHelper {

	/**
	 * Gets the name of the first <b>static final int</b> in <i>targetClass</i> that has the given <i>value</i>, and
	 * whose name starts with <i>prefix</i>, and ends with <i>suffix</i>. If no such constant can be found,
	 * <i>fallback</i> is returned instead.
	 */
	public static String getIntConstantName(Class<?> targetClass, int value, String prefix, String suffix, String fallback) {
		var foundField = Arrays.stream(targetClass.getFields()).filter(candidate -> {
			if (Modifier.isStatic(candidate.getModifiers()) && Modifier.isFinal(candidate.getModifiers())
					&& candidate.getName().startsWith(prefix) && candidate.getName().endsWith(suffix)
			) {
				try {
					return candidate.getLong(null) == value;
				} catch (IllegalAccessException e) {
					return false;
				}
			} else {
				return false;
			}
		}).findFirst();

		return foundField.map(Field::getName).orElse(fallback);
	}
}
