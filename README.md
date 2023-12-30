# vk-boiler
## Kill some boilerplate code in Vulkan Java projects
This is a library to reduce the amount of boilerplate code in Java Vulkan projects,
but without trying to abstract away from any Vulkan concepts (because it is pretty
much impossible to create a good abstraction that covers everything that can be
done with Vulkan). The functionality of `vk-boiler` can be subdivided in roughly 3
topics:

### 1. Providing many methods to perform common tasks
This is best illustrated with an example. Consider the task of creating a
one-time-submit command pool and command buffer. Without any boilerplate reduction,
it would probably look like this:
```java
try (var stack = stackPush()) {
    var ciCommandPool = VkCommandPoolCreateInfo.calloc(stack);
    ciCommandPool.sType$Default();
    ciCommandPool.flags(flags);
    ciCommandPool.queueFamilyIndex(queueFamilyIndex);

    var pCommandPool = stack.callocLong(1);
    assertVkSuccess(vkCreateCommandPool(
            instance.vkDevice(), ciCommandPool, null, pCommandPool
    ));
    var commandPool = pCommandPool.get(0);
    
    var aiCommandBuffer = VkCommandBufferAllocateInfo.calloc(stack);
    aiCommandBuffer.sType$Default();
    aiCommandBuffer.commandPool(commandPool);
    aiCommandBuffer.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY);
    aiCommandBuffer.commandBufferCount(1);

    var pCommandBuffer = stack.callocPointer(amount);
    assertVkSuccess(vkAllocateCommandBuffers(
        instance.vkDevice(), aiCommandBuffer, pCommandBuffer
    ));
    var commandBuffer = new VkCommandBuffer(pCommandBuffer.get(0), vkDevice)
}
```
Instead of writing all this, you could also write this:
```java
var commandPool = boiler.commands.createPool(
        0, boiler.queueFamilies().graphics().index(), "Copy"
);
var commandBuffer = boiler.commands.createPrimaryBuffers(
        commandPool, 1, "Copy"
)[0];
```
This library provides plenty of such convenience methods. By using them all whenever
possible, you can dramatically reduce the amount of code you need (at least,
I did when I started using this in my own projects).

#### When a task is not covered by this library
`vk-boiler` doesn't have a convenience method for everything that is possible with
Vulkan. For instance, when
- I couldn't find a way to simplify them in a meaningful way (e.g. render passes)
- The use case is niche (e.g. 1d and 3d images)
- When I simply didn't encounter the need in my own projects (pull requests are
welcome in this case)

When this library doesn't provide a nice method, you will need to do it the old way:
write all the code yourself. This should normally not be a problem since `vk-boiler`
doesn't hide your Vulkan handles from you.

### 2. Simplify bootstrapping
Bootstrapping Vulkan applications can be very verbose (often more than 100
lines of code). You need to:
- Query supported instance extensions and layers
- Create the instance
- Query physical devices, their properties, limits, extensions...
- Choose the best physical device (or abort execution)
- Query queue families
- Choose which queues you want to create
- Create the (logical) device
- And if you want to create a window as well, you need to take special care
in several of these steps...

Most of this work is very similar for almost all applications. To improve this
situation, `vk-boiler` provides a `BoilerBuilder` class that can be used to
do all of this with potentially very little code. It could be as simple as
this:
```java
var boiler = new BoilerBuilder(
    VK_API_VERSION_1_1, "SimpleRingApproximation", VK_MAKE_VERSION(0, 2, 0)
)
    .validation()
    .window(0L, 1000, 800, new BoilerSwapchainBuilder(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT))
    .build();
```
For more complex applications that want to use features and extensions, the
`BoilerBuilder` class provides several methods to help with that, for instance:
```java
var boiler = new BoilerBuilder(
    VK_API_VERSION_1_2, "TestComplexVulkan1.2", VK_MAKE_VERSION(1, 1, 1)
).engine("TestEngine", VK_MAKE_VERSION(0, 8, 4))
    .requiredVkInstanceExtensions(createSet(VK_KHR_GET_SURFACE_CAPABILITIES_2_EXTENSION_NAME))
    .desiredVkInstanceExtensions(createSet(VK_KHR_SURFACE_EXTENSION_NAME))
    .builder();
```
I tried to design the `BoilerBuilder` class to handle nearly all use cases, but I
probably missed some. If the `BoilerBuilder` lacks the flexibility you need, you can
still create the `BoilerInstance` using its public constructor.

### 3. Swapchain management
Almost every Vulkan application needs to do swapchain management, but the code to
accomplish this is usually the same. Furthermore, swapchain management can be quite
complicated (for instance, 
[when can you safely destroy your swapchain?](https://github.com/KhronosGroup/Vulkan-Samples/tree/main/samples/api/swapchain_recreation)
and did you know that Wayland swapchain images never become out of date?). 
To solve this problem, you can use`boiler.swapchains` and `SwapchainResourceManager`:
```java
var swapchainResources = new SwapchainResourceManager<>(swapchainImage -> {
        try (var stack = stackPush()) {
            long imageView = boiler.images.createSimpleView(
                stack, swapchainImage.vkImage(), boiler.swapchainSettings.surfaceFormat().format(),
                VK_IMAGE_ASPECT_COLOR_BIT, "SwapchainView" + swapchainImage.imageIndex()
            );

            long framebuffer = boiler.images.createFramebuffer(
                stack, renderPass, swapchainImage.width(), swapchainImage.height(),
                "RingFramebuffer", imageView
            );

            return new AssociatedSwapchainResources(framebuffer, imageView);
        }}, resources -> {
                vkDestroyFramebuffer(boiler.vkDevice(), resources.framebuffer, null);
                vkDestroyImageView(boiler.vkDevice(), resources.imageView, null);
        }
);
while (renderLoop) {
        glfwPollEvents();
        var swapchainImage=boiler.swapchains.acquireNextImage(VK_PRESENT_MODE_MAILBOX_KHR);
        if(swapchainImage==null){
            sleep(100);
            continue;
        }

        var imageResources=swapchainResources.get(swapchainImage);
        WaitSemaphore[]waitSemaphores={new WaitSemaphore(
            swapchainImage.acquireSemaphore(),VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
        )};

        // Do something (probably wait on some fence and start rendering)

        boiler.queueFamilies().graphics().queues().get(0).submit(
            commandBuffer,"RingApproximation",waitSemaphores,fence,swapchainImage.presentSemaphore()
        );

        boiler.swapchains.presentImage(swapchainImage);
}
```
While this is still rather verbose, it's a lot better than handling all the
swapchain recreation yourself. Just like everything else in this library, the
swapchain manager is also optional: you can freely ignore `boiler.swapchains`
if you want.

## Usage
This repository has a `samples` folder containing example applications that
demonstrate how you can use `vk-boiler` effectively. (In fact, many code
snippets in this `README` are copied from these projects.) The basic usage
comes down to this:
```java
var boiler = new BoilerBuilder(
        VK_API_VERSION_1_0, "Just an example", VK_MAKE_VERSION(1, 0, 0)
)
        // Configure window, validation, features, and extensions
        .build();

// Use the convenience methods like boiler.buffers.create(...)

// Render loop with boiler.swapchains...

boiler.destroyInitialObjects()
```
To learn about the rest of the features, just check the code completion
suggestions :)

## Add to your build
### Java version
This project requires Java 17 or later (Java 17 and 20 are tested in CI).
### LWJGL
While this project is compiled against LWJGL, it does **not** bundle LWJGL, so you
still need to declare the LWJGL dependencies yourself 
(hint: use [LWJGL customizer](https://www.lwjgl.org/customize)). This approach
allows you to control which version of LWJGL you want to use (as long as its
compatible with the version used by `vk-boiler`).
### Gradle
```
...
repositories {
  ...
  maven { url 'https://jitpack.io' }
}
...
dependencies {
  ...
  implementation 'com.github.knokko:vk-boiler:v2.1.0'
}
```

### Maven
```
...
<repositories>
  ...
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>
...
<dependency>
  <groupId>com.github.knokko</groupId>
  <artifactId>vk-boiler</artifactId>
  <version>v2.1.0</version>
</dependency>
```

## Error handling
Most `vk-boiler` methods use `VulkanFailureException.assertVkSuccess(...)` to test
the return values of all Vulkan function calls. This will throw a
`VulkanFailureException` when the return value is not `VK_SUCCESS` (or another
acceptable return code).

### BoilerBuilder.build() exceptions
The `build()` method of `BoilerBuilder` is a special method that can throw
several other exception types as well:
- `GLFWFailureException`: when it failed to initialize GLFW or create a window
- `MissingVulkanExtensionException`: when a required Vulkan extension is not
supported by the Vulkan implementation. Note: `vk-boiler` doesn't require any
extensions by default, but you can add required extensions to the `BoilerBuilder`,
plus GLFW will add some if you want to create a window.
- `MissingVulkanLayerException`: when a required Vulkan layer is not supported
by the Vulkan implementation. Note: `vk-boiler` doesn't require any
layers by default, but you can add required layers to the `BoilerBuilder`:
either explicitly or implicitly by enabling validation.
- `NoVkPhysicalDeviceException`: when not a single `VkPhysicalDevice` satisfies
all requirements of the `BoilerBuilder` (or the target machine doesn't have a
single Vulkan-capable graphics driver).

### Error handling
All exceptions provided by `vk-boiler` extend `RuntimeException`, so handling them
is completely optional. If you want a resilient application, you should probably
catch at least `VulkanFailureException` and try to recreate the `boiler` when an
unrecoverable error (like `VK_ERROR_DEVICE_LOST`) occurs. If you don't want anything
fancy, you can also just catch it, save user progress, and exit.