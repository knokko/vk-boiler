package com.github.knokko.boiler.queue;

import java.util.Collection;

public record QueueFamilies(
        QueueFamily graphics, QueueFamily compute, QueueFamily transfer,
        QueueFamily videoEncode, QueueFamily videoDecode, Collection<QueueFamily> allEnabledFamilies
) { }
