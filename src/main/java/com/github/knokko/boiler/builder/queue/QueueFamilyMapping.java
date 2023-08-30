package com.github.knokko.boiler.builder.queue;

import java.util.Arrays;

public record QueueFamilyMapping(
        int graphicsFamilyIndex, float[] graphicsPriorities,
        int computeFamilyIndex, float[] computePriorities,
        int transferFamilyIndex, float[] transferPriorities,
        int presentFamilyIndex
) {

    public void validate() throws IllegalStateException {
        validateFamilyPriorities(graphicsFamilyIndex, computeFamilyIndex, graphicsPriorities, computePriorities, "graphics", "compute");
        validateFamilyPriorities(graphicsFamilyIndex, transferFamilyIndex, graphicsPriorities, transferPriorities, "graphics", "transfer");
        validateFamilyPriorities(computeFamilyIndex, transferFamilyIndex, computePriorities, transferPriorities, "compute", "transfer");
    }

    private void validateFamilyPriorities(
            int index1, int index2, float[] priorities1, float[] priorities2, String name1, String name2
    ) throws IllegalStateException {
        if (priorities1 == null || priorities2 == null) throw new IllegalStateException("Priorities are null");
        if (index1 == index2 && !Arrays.equals(priorities1, priorities2)) {
            throw new IllegalStateException(String.format(
                    "%sIndex == %sIndex (%d), but %sPriorities (%s) != %sPriorities(%s)",
                    name1, name2, index1, name1, Arrays.toString(priorities1), name2, Arrays.toString(priorities2)
            ));
        }
    }
}
