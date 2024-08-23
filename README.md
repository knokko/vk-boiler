# vk-boiler
## Kill some boilerplate code in Vulkan Java projects
This is a library to reduce the amount of boilerplate code in Java Vulkan projects,
as well as adding several **optional** abstractions. This library intentionally
does **not** hide any of its handles (e.g. the `VkDevice`) from you, to ensure
that it does not take any power away from you.
The functionality of `vk-boiler` can be subdivided in roughly 3
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
Bootstrapping Vulkan applications can be very verbose (easily hundreds of lines
of code). You need to:
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
		.enableDynamicRendering()
		.addWindow(new WindowBuilder(1000, 8000, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT))
		.build();
```
See the [complete initialization documentation](docs/initialization.md) for
more information.

### 3. Swapchain management
Almost every Vulkan application needs to do swapchain management, but the code to
accomplish this is usually the same. Furthermore, swapchain management can be quite
complicated (for instance, 
[when can you safely destroy your swapchain?](https://github.com/KhronosGroup/Vulkan-Samples/tree/main/samples/api/swapchain_recreation)
and did you know that Wayland swapchain images never become out of date?).

`vk-boiler` provides a [swapchain management system](docs/swapchain.md)
that you can use to avoid rewriting swapchain management for every single
application, and to avoid some of the aforementioned complexity.

Note that the whole swapchain manager is *lazy*, so it won't allocate/create
anything if you don't use it.

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
This project requires Java 17 or later (Java 17 and 21 are tested in CI).
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
  implementation 'com.github.knokko:vk-boiler:v3.2.0'
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
  <version>v3.2.0</version>
</dependency>
```

## Error handling
Most `vk-boiler` methods use `VulkanFailureException.assertVkSuccess(...)` to test
the return values of all Vulkan function calls. This will throw a
`VulkanFailureException` when the return value is not `VK_SUCCESS` (or another
acceptable return code).

### BoilerBuilder.build() exceptions
The `build()` method of `BoilerBuilder` is a special method that can throw
several other exception types as well. See its doc comments for more information.

### Error handling
All exceptions provided by `vk-boiler` extend `RuntimeException`, so handling them
is completely optional. If you want a resilient application, you should probably
catch at least `VulkanFailureException` and try to recreate the `boiler` when an
unrecoverable error (like `VK_ERROR_DEVICE_LOST`) occurs. If you don't want anything
fancy, you can also just catch it, save user progress, and exit.