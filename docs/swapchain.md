# Swapchain management

The swapchain management system takes care of the following tasks:
- Acquiring images
- Presenting images
- Recreating swapchains (e.g. after resize)
- Destroying old swapchains
- Switching present modes

Furthermore, an optional window loop system is also provided to
decrease the amount of code that you need to write even more.

## The core swapchain management system
### Basic flow
Every instance of `VkbWindow` has two methods to acquire a swapchain image:
- `acquireSwapchainImageWithFence`: if you use this, you need to wait on
the `acquireSubmission` of the image before submitting a command buffer that
uses the swapchain image.
- `acquireSwapchainImageWithSemaphore`: if you use this, you need to
add the `acquireSemaphore` to the wait semaphores of the queue submission
that uses the swapchain image.

You need to call exactly 1 of these methods every frame. After submitting
the drawing command buffer, you need to call the `presentSwapchainImage`
method of the `VkbWindow`. When you have exactly 1 window, you can get the
instance from `boilerInstance.window()`.

### Example
The usage of this system is shown in
[the HelloTriangle sample](../samples/src/main/java/com/github/knokko/boiler/samples/HelloTriangle.java)
, and it comes down to this:
```java
while (!shouldCloseWindow) {
	AcquiredImage swapchainImage = boiler.window().acquireSwapchainImageWithSemaphore(presentMode);
	if (swapchainImage == null) {
		Thread.sleep(100);
		continue;
	}
	// Use swapchainImage.image() to record commands...
	WaitSemaphore[] waitSemaphores = {new WaitSemaphore(
		swapchainImage.acquireSemaphore(), VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
	)};
	boiler.queueFamilies().graphics().first().submit(
		commandBuffer, "SubmitDraw", waitSemaphores, fence, swapchainImage.presentSemaphore()
	);
	boiler.window().presentSwapchainImage(swapchainImage);
}
```
As you can see, you don't need to think about recreating and destroying swapchains
at all. You can also choose a different `presentMode` at every *acquire* if you
want, and the system will recreate the swapchain (if needed).

### When `swapchainImage` is `null`
When the swapchain management system fails to acquire a swapchain image, the
acquire methods will return `null`. This happens for instance when the window is
minimized, or rapidly resizing. I think the most reasonable course of action in
such cases is to simply sleep a while and retry.

### Associating resources with swapchain images
The swapchain management system will automatically create and destroy swapchains
and their images, but there is sometimes a need to create objects that are
'tied' to swapchains or swapchain images (like swapchain framebuffers). You can
use a `SwapchainResourceManager<S, I>` to achieve this result, where the `S` is
the type associated with your swapchains, and `I` is the type associated with
your swapchain **images**.

When you want to associate 1 object per swapchain (image), `S` or `I` should be
the type of this object. When you want to associate multiple objects, you should
create a class or record that contains all these objects, and use that as `S` or
`I`. To use this class, you should create a class that extends
`SwapchainResourceManager`.
- To associate resources with swapchain images, you should override the
  `I createImage(S swapchain, AcquiredImage swapchainImage)` method and the
  `void destroyImage(I resource)` method.
- To associate resources with swapchains, you should override the
  `S createSwapchain(int width, int height, int numImages)` method and the
  `void destroySwapchain(S swapchain)` method. You **could** also override
  `S recreateSwapchain(S oldSwapchain, int newWidth, int newHeight, int newNumImages)`,
  but this is **optional**, and only useful when you want to recycle your
  associated swapchain resources.

The resource manager will ensure that the *create* methods are always called
whenever swapchains are created, or swapchain images are used for the first
time.

Furthermore, the resource manager will ensure that the *destroy* methods are
eventually called after their corresponding swapchain is destroyed.
(But not immediately because they are potentially needed by the
`recreateSwapchain` method.)
You don't need to destroy the resource manager explicitly.

To get the resources associated with a swapchain image, use
`yourSwapchainResourceManager.get(swapchainImage)`. The relevant code in
HelloTriangle is shown below:
```java
var swapchainResources = new SwapchainResourceManager<Object, Long>() {

    @Override
    protected Long createImage(Object swapchain, AcquiredImage swapchainImage) {
        return boiler.images.createFramebuffer(
                renderPass, swapchainImage.width(), swapchainImage.height(),
                "TriangleFramebuffer", swapchainImage.image().vkImageView
        );
    }

    @Override
    protected void destroyImage(Long framebuffer) {
        try (var stack = stackPush()) {
            vkDestroyFramebuffer(
                    boiler.vkDevice(), framebuffer,
                    CallbackUserData.FRAME_BUFFER.put(stack, boiler)
            );
        }
    }
};
// Some unrelated stuff...
while (windowShouldNotClose) {
	// Acquire swapchainImage...
	var framebuffer = swapchainResources.get(swapchainImage);
	// Use framebuffer and do the rest of the frame...
}
```

The `TerrainPlayground` sample shows a more advanced way to use
`SwapchainResourceManager`.

## Window loop systems
The basic usage shown above is simple and requires much less code than doing
swapchain management yourself, but it has some flaws:
1. Every application still has boilerplate code for the render loop,
acquiring the images, possibly sleeping, and presenting the image.
2. No smooth resizing on Windows
3. This loop is unsuitable for dealing with multiple windows

By using the window loop systems of `vk-boiler`, you can also eliminate these
flaws.

### The `WindowRenderLoop` class
The `WindowRenderLoop` class intends to solve problem (1). Applications can
create a class that extends `WindowRenderLoop`, and:
- Call its `start()` method on the *main thread* (typically during the
`main` method)
- Implement the `setup` method
- Implement the `renderFrame` method
- Implement the `cleanUp` method

The [SimpleRingApproximation sample](../samples/src/main/java/com/github/knokko/boiler/samples/SimpleRingApproximation.java)
can be used as an example.

### The `SimpleWindowRenderLoop` class
When all your applications use the `WindowRenderLoop` class, a lot of
boilerplate code will be gone, but there is still some left: the
creation and destruction of all the rendering command pools/buffers
and the associated fences. There is also some boilerplate code to
transition the swapchain image to/from the presentation layout.

When your application has a simple single-threaded rendering set-up,
you can use the `SimpleWindowRenderLoop` to get rid of this boilerplate
code as well. This is a simple subclass of `WindowRenderLoop` that:
- creates command pools, command buffers, and fences during the
`setup` method
- destroys these command pools and fences during the `cleanUp` method
- begins command buffer recording during `renderFrame`, transitions
the swapchain image layout to the right layout, calls the
abstract `recordCommands` method, transitions the swapchain image
to `VK_IMAGE_LAYOUT_PRESENT_SRC_KHR`, and then submits the command buffer.

When your application uses this, you need to:
- Call its `start()` method on the *main thread* (typically during the
`main` method)
- Implement the `recordCommands` method
- Pass the right parameters to the constructor of
`SimpleWindowRenderLoop`

### The `WindowEventLoop` class
To tackle problems (2) and (3), some multithreading is required:
- The main thread needs to handle GLFW (or SDL) events
  and (re)create swapchains
- The render thread(s) need(s) to render and present the swapchain images

The `WindowEventLoop` class claims the main thread to wait for GLFW
(or SDL) events and recreate swapchains, while the
`(Simple)WindowRenderLoop` class handles the rendering and
presentation. The usage is:
```java
var eventLoop = new WindowEventLoop();
eventLoop.addWindow(new ClassThatExtendsWindowRenderLoop(boiler.window()));
eventLoop.runMain();
```
The [TranslucentWindowPlayground sample](../samples/src/main/java/com/github/knokko/boiler/samples/TranslucentWindowPlayground.java)
demonstrates this. If you want to also run your own code on the
main thread, you can use the
`WindowEventLoop(waitTimeout, updateCallback)` constructor to
periodically call your `updateCallback` on the main thread.

### Using multiple windows
Given the code snippet above, adding more windows is trivial:
```java
var eventLoop = new WindowEventLoop();
eventLoop.addWindow(new ClassThatExtendsWindowRenderLoop(window1));
eventLoop.addWindow(new AnotherClassThatExtendsWindowRenderLoop(window2));
eventLoop.runMain();
```
The [MultipleWindows sample](../samples/src/main/java/com/github/knokko/boiler/samples/MultipleWindows.java)
demonstrates this.
