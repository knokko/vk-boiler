package com.github.knokko.boiler.builder.queue;

import java.util.Arrays;

public record QueueFamilyMapping(
        QueueFamilyAllocation graphics,
        QueueFamilyAllocation compute,
        QueueFamilyAllocation transfer,
        QueueFamilyAllocation videoEncode,
        QueueFamilyAllocation videoDecode,
        int presentFamilyIndex
) {

    public void validate() throws IllegalStateException {
        validateFamilyPriorities(graphics, compute, "graphics", "compute");
        validateFamilyPriorities(graphics, transfer, "graphics", "transfer");
        validateFamilyPriorities(compute, transfer, "compute", "transfer");
        if (videoEncode != null) {
            validateFamilyPriorities(graphics, videoEncode, "graphics", "videoEncode");
            validateFamilyPriorities(compute, videoEncode, "compute", "videoEncode");
            validateFamilyPriorities(transfer, videoEncode, "transfer", "videoEncode");
        }
        if (videoDecode != null) {
            validateFamilyPriorities(graphics, videoDecode, "graphics", "videoDecode");
            validateFamilyPriorities(compute, videoDecode, "compute", "videoDecode");
            validateFamilyPriorities(transfer, videoDecode, "transfer", "videoDecode");
        }
        if (videoEncode != null && videoDecode != null) {
            validateFamilyPriorities(videoEncode, videoDecode, "videoEncode", "videoDecode");
        }
    }

    private void validateFamilyPriorities(
            QueueFamilyAllocation allocation1, QueueFamilyAllocation allocation2, String name1, String name2
    ) throws IllegalStateException {
        if (allocation1.priorities() == null || allocation2.priorities() == null) {
            throw new IllegalStateException("Priorities are null");
        }
        if (allocation1.index() == allocation2.index() &&
                !Arrays.equals(allocation1.priorities(), allocation2.priorities())) {
            throw new IllegalStateException(String.format(
                    "%sIndex == %sIndex (%d), but %sPriorities (%s) != %sPriorities(%s)",
                    name1, name2, allocation1.index(), name1, Arrays.toString(allocation1.priorities()),
                    name2, Arrays.toString(allocation2.priorities())
            ));
        }
    }
}
