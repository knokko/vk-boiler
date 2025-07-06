package com.github.knokko.boiler;

import java.util.Set;

/**
 * Captures the layers, instance extensions, and device extensions that were enabled, as well as some features that
 * are important for vk-boiler internally.
 * @param layers The (instance) layers that were <i>explicitly</i> enabled.
 *               This does <b>not</b> include implicit layers!
 * @param instanceExtensions The instance extensions that we enabled
 * @param deviceExtensions The device extensions that we enabled
 * @param swapchainMaintenance True if and only if we enabled the swapchain maintenance feature
 * @param memoryPriority True if and only if we enabled the memory priority feature
 * @param pageableMemory True if and only if we enabled the pageable device local memory feature
 */
public record BoilerExtra(
		Set<String> layers,
		Set<String> instanceExtensions,
		Set<String> deviceExtensions,

		boolean swapchainMaintenance,
		boolean memoryPriority,
		boolean pageableMemory
) { }
