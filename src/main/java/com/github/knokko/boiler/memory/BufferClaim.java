package com.github.knokko.boiler.memory;

import com.github.knokko.boiler.buffers.VkbBuffer;

record BufferClaim(VkbBuffer buffer, long alignment) {}
