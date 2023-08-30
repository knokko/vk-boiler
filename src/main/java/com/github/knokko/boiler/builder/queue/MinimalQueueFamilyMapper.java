package com.github.knokko.boiler.builder.queue;

import org.lwjgl.vulkan.VkQueueFamilyProperties;

import static org.lwjgl.vulkan.VK10.VK_QUEUE_COMPUTE_BIT;
import static org.lwjgl.vulkan.VK10.VK_QUEUE_GRAPHICS_BIT;

public class MinimalQueueFamilyMapper implements QueueFamilyMapper {

    @Override
    public QueueFamilyMapping mapQueueFamilies(VkQueueFamilyProperties.Buffer queueFamilies, boolean[] presentSupport) {
        float[] priorities = { 1f };
        int graphicsFamilyIndex = -1;
        int computeFamilyIndex = -1;
        int presentFamilyIndex = -1;

        for (int familyIndex = 0; familyIndex < queueFamilies.limit(); familyIndex++) {
            int queueFlags = queueFamilies.get(familyIndex).queueFlags();
            boolean hasGraphics = (queueFlags & VK_QUEUE_GRAPHICS_BIT) != 0;
            boolean hasCompute = (queueFlags & VK_QUEUE_COMPUTE_BIT) != 0;
            boolean hasPresent = presentSupport[familyIndex];

            if (hasGraphics && hasCompute && hasPresent) {
                return new QueueFamilyMapping(
                        familyIndex, priorities, familyIndex, priorities, familyIndex, priorities, familyIndex
                );
            }

            if (graphicsFamilyIndex == -1 && hasGraphics) graphicsFamilyIndex = familyIndex;
            if (computeFamilyIndex == -1 && hasCompute) computeFamilyIndex = familyIndex;
            if (hasGraphics && hasCompute) {
                graphicsFamilyIndex = familyIndex;
                computeFamilyIndex = familyIndex;
            }

            if (presentFamilyIndex == -1 && hasPresent) presentFamilyIndex = familyIndex;
            if ((hasGraphics || hasCompute) && hasPresent) presentFamilyIndex = familyIndex;
        }

        return new QueueFamilyMapping(
                graphicsFamilyIndex, priorities,
                computeFamilyIndex, priorities,
                graphicsFamilyIndex, priorities, presentFamilyIndex
        );
    }
}
