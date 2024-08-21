package com.github.knokko.boiler.utilities;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;

public class ReflectionHelper {

	public static String getIntConstantName(Class<?> targetClass, int value, String prefix, String suffix, String fallback) {
		var foundField = Arrays.stream(targetClass.getFields()).filter(candidate -> {
			if (Modifier.isStatic(candidate.getModifiers()) && Modifier.isFinal(candidate.getModifiers())
					&& candidate.getName().startsWith(prefix) && candidate.getName().endsWith(suffix)
			) {
				try {
					return candidate.getInt(null) == value;
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
