package com.github.knokko.boiler.builders.instance;

public record ValidationFeatures(
		boolean gpuAssisted, boolean debugPrint,
		boolean bestPractices, boolean synchronization
) {

	@Override
	public String toString() {
		return String.format(
				"Validation(gpu=%b, print=%b, best=%b, sync=%b)",
				gpuAssisted, debugPrint, bestPractices, synchronization
		);
	}
}
