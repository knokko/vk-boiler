package com.github.knokko.boiler.queue;

import java.util.List;

public record QueueFamily(int index, List<BoilerQueue> queues) {}
