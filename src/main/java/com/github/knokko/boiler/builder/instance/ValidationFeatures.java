package com.github.knokko.boiler.builder.instance;

public record ValidationFeatures(
        boolean gpuAssisted, boolean gpuAssistedReserve, boolean debugPrint,
        boolean bestPractices, boolean synchronization
) {

    @Override
    public String toString() {
        return String.format(
                "Validation(gpu=%b, reserve=%b, print=%b, best=%b, sync=%b)",
                gpuAssisted, gpuAssistedReserve, debugPrint, bestPractices, synchronization
        );
    }
}
