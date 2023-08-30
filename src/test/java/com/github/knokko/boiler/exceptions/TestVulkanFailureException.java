package com.github.knokko.boiler.exceptions;

import org.junit.jupiter.api.Test;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.vulkan.VK10.*;

public class TestVulkanFailureException {

    @Test
    public void testSuccess() {
        assertVkSuccess(VK_SUCCESS, "Success", "Test");
    }

    @Test
    public void testAllowedSuccess() {
        assertVkSuccess(VK_INCOMPLETE, "AllowedSuccess", "Test", VK_INCOMPLETE);
    }

    @Test
    public void testFailure() {
        var exception = assertThrows(VulkanFailureException.class, () ->
            assertVkSuccess(VK_TIMEOUT, "CreateDevice", "Test")
        );
        assertEquals(VK_TIMEOUT, exception.result);
        assertEquals("vkCreateDevice", exception.functionName);
        assertEquals("Test", exception.context);
        assertEquals("vkCreateDevice (Test) returned 2 (VK_TIMEOUT)", exception.getMessage());
    }

    @Test
    public void testOtherFailure() {
        var exception = assertThrows(VulkanFailureException.class, () ->
            assertVkSuccess(VK_ERROR_DEVICE_LOST, "vkCreateDevice", null, VK_TIMEOUT)
        );
        assertEquals(VK_ERROR_DEVICE_LOST, exception.result);
        assertEquals("vkCreateDevice", exception.functionName);
        assertNull(exception.context);
        assertEquals("vkCreateDevice returned -4 (VK_ERROR_DEVICE_LOST)", exception.getMessage());
    }
}
