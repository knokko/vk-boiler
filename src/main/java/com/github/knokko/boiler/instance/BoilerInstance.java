package com.github.knokko.boiler.instance;

import com.github.knokko.boiler.buffer.BoilerBuffers;
import com.github.knokko.boiler.commands.BoilerCommands;
import com.github.knokko.boiler.debug.BoilerDebug;
import com.github.knokko.boiler.descriptors.BoilerDescriptors;
import com.github.knokko.boiler.images.BoilerImages;
import com.github.knokko.boiler.pipelines.BoilerPipelines;
import com.github.knokko.boiler.queue.QueueFamilies;
import com.github.knokko.boiler.surface.WindowSurface;
import com.github.knokko.boiler.swapchain.BoilerSwapchains;
import com.github.knokko.boiler.swapchain.SwapchainSettings;
import com.github.knokko.boiler.sync.BoilerSync;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkPhysicalDevice;

import java.util.Collections;
import java.util.Set;

import static org.lwjgl.glfw.GLFW.glfwDestroyWindow;
import static org.lwjgl.util.vma.Vma.vmaDestroyAllocator;
import static org.lwjgl.vulkan.VK10.vkDestroyDevice;
import static org.lwjgl.vulkan.VK10.vkDestroyInstance;

public class BoilerInstance {

    private final long glfwWindow;
    private final WindowSurface windowSurface;
    public final SwapchainSettings swapchainSettings;

    private final VkInstance vkInstance;
    private final VkPhysicalDevice vkPhysicalDevice;
    private final VkDevice vkDevice;
    public final Set<String> instanceExtensions, deviceExtensions;
    private final QueueFamilies queueFamilies;
    private final long vmaAllocator;

    public final BoilerBuffers buffers;
    public final BoilerImages images;
    public final BoilerDescriptors descriptors;
    public final BoilerPipelines pipelines;
    public final BoilerCommands commands;
    public final BoilerSync sync;
    public final BoilerSwapchains swapchains;
    public final BoilerDebug debug;

    private boolean destroyed = false;

    public BoilerInstance(
            long glfwWindow, WindowSurface windowSurface, SwapchainSettings swapchainSettings,
            VkInstance vkInstance, VkPhysicalDevice vkPhysicalDevice, VkDevice vkDevice,
            Set<String> instanceExtensions, Set<String> deviceExtensions,
            QueueFamilies queueFamilies, long vmaAllocator
    ) {
        this.glfwWindow = glfwWindow;
        this.windowSurface = windowSurface;
        this.swapchainSettings = swapchainSettings;
        this.vkInstance = vkInstance;
        this.vkPhysicalDevice = vkPhysicalDevice;
        this.vkDevice = vkDevice;
        this.instanceExtensions = Collections.unmodifiableSet(instanceExtensions);
        this.deviceExtensions = Collections.unmodifiableSet(deviceExtensions);
        this.queueFamilies = queueFamilies;
        this.vmaAllocator = vmaAllocator;

        this.buffers = new BoilerBuffers(this);
        this.images = new BoilerImages(this);
        this.descriptors = new BoilerDescriptors(this);
        this.pipelines = new BoilerPipelines(this);
        this.commands = new BoilerCommands(this);
        this.sync = new BoilerSync(this);
        this.swapchains = swapchainSettings != null ? new BoilerSwapchains(this) : null;
        this.debug = new BoilerDebug(this);
    }

    private void checkDestroyed() {
        if (destroyed) throw new IllegalStateException("This instance has already been destroyed");
    }

    private void checkWindow() {
        if (glfwWindow == 0L) throw new UnsupportedOperationException("This instance doesn't have a window");
    }

    public long glfwWindow() {
        checkDestroyed();
        checkWindow();
        return glfwWindow;
    }

    public WindowSurface windowSurface() {
        checkDestroyed();
        checkWindow();
        return windowSurface;
    }

    public VkInstance vkInstance() {
        checkDestroyed();
        return vkInstance;
    }

    public VkPhysicalDevice vkPhysicalDevice() {
        checkDestroyed();
        return vkPhysicalDevice;
    }

    public VkDevice vkDevice() {
        checkDestroyed();
        return vkDevice;
    }

    public QueueFamilies queueFamilies() {
        checkDestroyed();
        return queueFamilies;
    }

    public long vmaAllocator() {
        checkDestroyed();
        return vmaAllocator;
    }

    public void destroy() {
        checkDestroyed();

        if (swapchains != null) swapchains.destroy();
        vmaDestroyAllocator(vmaAllocator);
        vkDestroyDevice(vkDevice, null);
        if (windowSurface != null) windowSurface.destroy(vkInstance);
        vkDestroyInstance(vkInstance, null);
        if (glfwWindow != 0L) glfwDestroyWindow(glfwWindow);

        destroyed = true;
    }
}
