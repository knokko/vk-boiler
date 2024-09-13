package com.github.knokko.boiler.queues;

import java.util.Collection;

/**
 * The queue families for which at least 1 queue was created during device creation.
 * @param graphics The family intended for graphics operations, which is guaranteed to support them.
 * @param compute The family intended for compute operations, which is guaranteed to support them. Note that this is
 *                usually the same family as the graphics family, unless you chain a different <i>queueFamilyMapper</i>
 *                to the <i>BoilerBuilder</i>
 * @param transfer The family intended for transfer operations, which is guaranteed to support them. Note that this is
 *                 usually the same as the graphics family.
 * @param videoEncode When any video encoding extension was enabled, this will be the queue family intended for video
 *                    encode operations. Otherwise, it will be null.
 * @param videoDecode When any video decoding extension was enabled, this will be the queue family intended for video
 *  *                 decode operations. Otherwise, it will be null.
 * @param allEnabledFamilies A collection that contains all queue families above, plus the queue families that were
 *                           created only for surface presentation.
 */
public record QueueFamilies(
		VkbQueueFamily graphics, VkbQueueFamily compute, VkbQueueFamily transfer,
		VkbQueueFamily videoEncode, VkbQueueFamily videoDecode, Collection<VkbQueueFamily> allEnabledFamilies
) {
}
