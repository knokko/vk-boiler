package com.github.knokko.boiler.development;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class FastUtil {

	public static void main(String[] args) throws IOException, InterruptedException, ClassNotFoundException {
		var process = Runtime.getRuntime().exec(new String[] {
				"jdeps", "-R", "-verbose:class", "../build/classes", "fastutil-8.5.15.jar" }
		);

		String targetPackage = "it.unimi.dsi.fastutil.";
		var input = process.inputReader();
		var possiblyRelevantLines = input.lines().filter(line -> line.split("->")[1].contains(targetPackage)).toList();

		var relevantClasses = possiblyRelevantLines.stream().filter(
				line -> !line.split("->")[0].contains(targetPackage)
		).map(line -> line.split("->")[1].trim().split(" ")[0]).collect(Collectors.toSet());
		relevantClasses.add("it.unimi.dsi.fastutil.HashCommon");

		while (true) {
			int oldSize = relevantClasses.size();
			var newClasses = new HashSet<>(relevantClasses);
			for (String className : relevantClasses) {
				var currentClass = Class.forName(className);
				var superClass = currentClass.getSuperclass();
				if (superClass != null && superClass.getName().contains(targetPackage)) newClasses.add(superClass.getName());

				for (var superInterface : currentClass.getInterfaces()) {
					if (superInterface.getName().contains(targetPackage)) newClasses.add(superInterface.getName());
				}

				for (var field : currentClass.getDeclaredFields()) {
					var fieldClass = field.getType();
					if (fieldClass.getName().contains(targetPackage)) newClasses.add(fieldClass.getName());
				}
			}
			relevantClasses = newClasses;
			if (oldSize == relevantClasses.size()) break;
		}

		if (!process.waitFor(5, TimeUnit.SECONDS) || process.exitValue() != 0) {
			System.out.println("jdeps failed");
			var errors = process.errorReader();
			while (errors.ready()) {
				System.err.println(errors.readLine());
			}
			errors.close();
			input.close();
			process.destroy();
			return;
		}

		input.close();

		var bigJar = new ZipInputStream(Files.newInputStream(new File("fastutil-8.5.15.jar").toPath()));
		var smallJar = new ZipOutputStream(Files.newOutputStream(new File("fastutil-mini.jar").toPath()));
		while (true) {
			var entry = bigJar.getNextEntry();
			if (entry == null) break;
			if (relevantClasses.contains(entry.getName().replace('/', '.').replace(".class", ""))) {
				smallJar.putNextEntry(new ZipEntry(entry.getName()));
				smallJar.write(bigJar.readAllBytes());
			}
		}
		smallJar.flush();
		smallJar.close();
		bigJar.close();

		System.out.println("Done");
	}
}
