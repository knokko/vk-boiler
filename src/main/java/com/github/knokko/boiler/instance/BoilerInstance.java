package com.github.knokko.boiler.instance;

import com.github.knokko.boiler.buffer.BoilerBuffers;
import com.github.knokko.boiler.builder.WindowBuilder;
import com.github.knokko.boiler.commands.BoilerCommands;
import com.github.knokko.boiler.debug.BoilerDebug;
import com.github.knokko.boiler.descriptors.BoilerDescriptors;
import com.github.knokko.boiler.images.BoilerImages;
import com.github.knokko.boiler.pipelines.BoilerPipelines;
import com.github.knokko.boiler.queue.QueueFamilies;
import com.github.knokko.boiler.sync.BoilerSync;
import com.github.knokko.boiler.window.VkbWindow;
import com.github.knokko.boiler.xr.XrBoiler;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkPhysicalDevice;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import static org.lwjgl.util.vma.Vma.vmaDestroyAllocator;
import static org.lwjgl.vulkan.EXTDebugUtils.vkDestroyDebugUtilsMessengerEXT;
import static org.lwjgl.vulkan.VK10.*;

public class BoilerInstance {

    public final long defaultTimeout;

    private final Collection<VkbWindow> windows;
    private final boolean hasSwapchainMaintenance;

    private final XrBoiler xr;

    public final int apiVersion;
    private final VkInstance vkInstance;
    private final VkPhysicalDevice vkPhysicalDevice;
    private final VkDevice vkDevice;
    public final Set<String> explicitLayers, instanceExtensions, deviceExtensions;
    private final QueueFamilies queueFamilies;
    private final long vmaAllocator;
    private final long validationErrorThrower;

    public final BoilerBuffers buffers;
    public final BoilerImages images;
    public final BoilerDescriptors descriptors;
    public final BoilerPipelines pipelines;
    public final BoilerCommands commands;
    public final BoilerSync sync;
    public final BoilerDebug debug;

    private boolean destroyed = false;

    public BoilerInstance(
            XrBoiler xr, long defaultTimeout, Collection<VkbWindow> windows, boolean hasSwapchainMaintenance,
            int apiVersion, VkInstance vkInstance, VkPhysicalDevice vkPhysicalDevice, VkDevice vkDevice,
            Set<String> explicitLayers, Set<String> instanceExtensions, Set<String> deviceExtensions,
            QueueFamilies queueFamilies, long vmaAllocator, long validationErrorThrower
    ) {
        this.windows = windows;
        this.hasSwapchainMaintenance = hasSwapchainMaintenance;
        this.xr = xr;
        this.defaultTimeout = defaultTimeout;
        this.apiVersion = apiVersion;
        this.vkInstance = vkInstance;
        this.vkPhysicalDevice = vkPhysicalDevice;
        this.vkDevice = vkDevice;
        this.explicitLayers = Collections.unmodifiableSet(explicitLayers);
        this.instanceExtensions = Collections.unmodifiableSet(instanceExtensions);
        this.deviceExtensions = Collections.unmodifiableSet(deviceExtensions);
        this.queueFamilies = queueFamilies;
        this.vmaAllocator = vmaAllocator;
        this.validationErrorThrower = validationErrorThrower;

        this.buffers = new BoilerBuffers(this);
        this.images = new BoilerImages(this);
        this.descriptors = new BoilerDescriptors(this);
        this.pipelines = new BoilerPipelines(this);
        this.commands = new BoilerCommands(this);
        this.sync = new BoilerSync(this);
        this.debug = new BoilerDebug(this);

        for (var window : windows) window.setInstance(this);
    }

    private void checkDestroyed() {
        if (destroyed) throw new IllegalStateException("This instance has already been destroyed");
    }

    public VkbWindow window() {
        checkDestroyed();
        if (windows.isEmpty()) throw new UnsupportedOperationException("This boiler doesn't have a window");
        if (windows.size() > 1) {
            throw new UnsupportedOperationException(
                    "This method can't be used when there are multiple windows. " +
                            "You should chain a .callback(...) to the corresponding WindowBuilder to get its instance"
            );
        }
        return windows.iterator().next();
    }

    public boolean hasSwapchainMaintenance() {
        return hasSwapchainMaintenance;
    }

    public VkbWindow addWindow(WindowBuilder builder) {
        checkDestroyed();
        return builder.buildLate(this);
    }

    public XrBoiler xr() {
        checkDestroyed();
        if (xr == null) throw new UnsupportedOperationException("This BoilerInstance doesn't have OpenXR support");
        return xr;
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

    /**
     * Destroys the GLFW objects and the Vulkan objects that were created during <i>BoilerBuilder.build()</i> (or were
     * passed to the constructor of this class if <i>BoilerBuilder</i> wasn't used). A list of objects that will be
     * destroyed:
     * <ul>
     *     <li>All windows (if any), alongside their swapchains and surfaces</li>
     *     <li>The returned fences in the fence bank</li>
     *     <li>The unused semaphores in the semaphore bank</li>
     *     <li>The VMA allocator</li>
     *     <li>The VkDevice</li>
     *     <li>The validation error thrower (if applicable)</li>
     *     <li>The VkInstance</li>
     *     <li>The OpenXR instance (if applicable)</li>
     * </ul>
     */
    public void destroyInitialObjects() {
        checkDestroyed();

        for (var window : windows) window.destroy();
        sync.fenceBank.destroy();
        sync.semaphoreBank.destroy();
        vmaDestroyAllocator(vmaAllocator);
        vkDestroyDevice(vkDevice, null);
        if (validationErrorThrower != VK_NULL_HANDLE) {
            vkDestroyDebugUtilsMessengerEXT(vkInstance, validationErrorThrower, null);
        }
        vkDestroyInstance(vkInstance, null);
        //if (glfwWindow != 0L) glfwDestroyWindow(glfwWindow);
        if (xr != null) xr.destroyInitialObjects();

        destroyed = true;
    }
}
