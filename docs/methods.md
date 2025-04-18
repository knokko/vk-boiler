# The helper methods of vk-boiler
vk-boiler provides many helper methods to reduce the amount of boilerplate
code in projects, as well as some potentially useful classes.

## Buffers
Creating buffers and allocating their memory in raw Vulkan is a lot of
work. VMA significantly improves the situation, but it remains rather
verbose.

### DeviceVkbBuffer
The `DeviceVkbBuffer` class is a tuple of a `VkBuffer`, its `VmaAllocation`
(optional), and its size. It represents a buffer that is probably **not**
host-visible. To create one, you should use
`boiler.buffers.create(size, usage, name)` or 
`boiler.buffers.createRaw(size, usage, name)`. The first option will use VMA
to allocate/bind memory for the buffer, whereas the second option won't
allocate/bind anything.

### MappedVkbBuffer
The `MappedVkbBuffer` class is a tuple of a `VkBuffer`, its `VmaAllocation`,
(optional), its size, and its mapped memory address. It represents a
host-visible buffer whose memory is always mapped. To create one, use
`boiler.buffers.createMapped(size, usage, name)`.

### VkbBufferRange
The `VkbBufferRange` represents a range of a `VkbBuffer`, which is just a
tuple `(buffer, byteOffset, byteSize)`. You can obtain an instance by
calling the `range(...)` or `fullRange()` method of a `DeviceVkbBuffer` or
a `MappedVkbBuffer`.

### MappedVkbBufferRange
The `MappedVkbBufferRange` represents a range of a `MappedVkbBuffer`,
which is a tuple `(mappedBuffer, byteOffset, byteSize)`. You can obtain
an instance by calling the `mappedRange(...)` or `mappedFullRange()`
method of a `MappedVkbBuffer`. It provides `byteBuffer()`,
`shortBuffer()`, etc... methods to create Java NIO buffers whose memory
is backed by the buffer range. It also provides a `range(...)` method to
create a corresponding `VkbBufferRange`.

### PerFrameBuffer
The `PerFrameBuffer` wraps a `MappedVkbBufferRange`, and uses it to manage
one-time-only data that you use every frame, and whose memory space can be
reused after `numberOfFramesInFlight` frames. You can use it to easily
share such space with multiple independent renderers.

### Encoding/decoding images
You can use `boiler.buffers.encodeBufferedImageRGBA(...)` to encode/store a
`BufferedImage` in a `MappedVkbBuffer` in RGBA8 format. You can use this to
fill a staging buffer with image data such that it can be used in
`vkCmdCopyBufferToImage(...)`.

Likewise, you can use `boiler.buffers.decodeBufferedImageRGBA(...)` to
decode/load a `BufferedImage` from a `MappedVkbBuffer`. You can use this
after `vkCmdCopyImageToBuffer(...)` to read the contents of a `VkImage`.
I find this very convenient for debugging.

There is also `encodeBufferedIntoRangeRGBA` and
`decodeBufferedImageFromRangeRGBA`, which use a `MappedVkbBufferRange`
instead of a `MappedVkbBuffer` and offset.

## Commands
Pretty much any Vulkan application needs command buffers, but using them
can be quite verbose. The following features are provided to get rid of
some boilerplate code.

### Command pools
You can use `boiler.commands.createPool(flags, queueFamilyIndex, name)`
to create a command pool with the given creation flags and queue
family index, using just 1 line of code. You can also use
`boiler.commands.createPools(flags, queueFamilyIndex, amount, name)` to
create an array of command pools in 1 line of code.

### Command buffer allocation
You can use `boiler.commands.createPrimaryBuffers(commandPool, amount, name)`
to allocate *amount* primary command buffers from the given command pool.
You can also use `boiler.commands.createPrimaryBufferPerPool(name, pools)`
to allocate 1 primary command buffer for each given command pool.

### Beginning command buffers
You can use
`boiler.commands.begin(commandBuffer, memoryStack, flags?, context)`
to *begin* a command buffer using a single line of code. Note however
that using `CommandRecorder.begin` is usually a better option.

### CommandRecorder
The `CommandRecorder` class is the recommended way to record command
buffers. It is a wrapper around a `VkCommandBuffer` that is being
recorded, and it provides a lot of methods to perform common commands
using few lines of code. Use `CommandRecorder.begin(...)` to **begin**
recording a command buffer, or `CommandRecorder.alreadyRecording(...)`
to wrap a command buffer that is already being recorded. Use code
completion and/or the source code to explore all possible options.

### SingleTimeCommands
The `SingleTimeCommands` class is the recommend way to execute
one-time-submit commands. Using it is as simple as
```java
var commands = new SingleTimeCommands(boiler);
commands.submit("Example", recorder -> {
		recorder.copyBufferRanges(...); // Just an example
		// Or do something with recorder.commandBuffer
		// And note that you can also use recorder.stack
}).awaitCompletion(); // The awaitCompletion() is optional
// Optional: reuse this instance later with different commands
commands.destroy();
```
This could spare you all the boilerplate code of creating the
command pool, allocating the command buffer, beginning the
command buffer, ending the command buffer, submitting the
command buffer, and awaiting its fence.

## Culling
The `FrustumCuller` class can be used to test whether a given
camera can see a given `AABB` (axis-aligned bounding box),
assuming that it is not obstructed by anything else. This is a
pure math problem and has absolutely nothing to do with Vulkan,
but it's included in `vk-boiler` because it's a very simple
and powerful optimization.

To use this class, create a `FrustumCuller` instance using one
of the constructors (you need 1 `FrustumCuller` per camera).
Then, use `frustumCuller.shouldCullAABB(someObject.aabb)` to
check whether you can skip rendering `someObject`.

## Debug
Using the `VK_EXT_debug_utils` extension can be pretty verbose,
so `vk-boiler` offers some methods to do this with less code.

### Naming objects
You can use `boiler.debug.name(stack, object, type, name)` to
call `vkSetDebugUtilsObjectNameEXT`. When the debug utils
extension is not enabled, this won't do anything. Note that
most methods of `vk-boiler` already call `debug.name(...)`,
so you usually don't need to call this explicitly (this is
why so many methods of `vk-boiler` have a `String name`
parameter).

### Creating messengers
You can use `boiler.debug.createMessenger(...)` to call
`vkCreateDebugUtilsMessengerEXT`. When the debug utils
extension is not enabled, this method will simply
return `VK_NULL_HANDLE`. Note that the validation layer
will simply print all output to the standard output
when you don't create any debug messengers. Thus, you
do **not** need this method if you only want to print
the validation errors. You should only use this method
if you want to do something 'special' with the
validation errors.

### Exceptions
Almost all methods of `vk-boiler` will throw a
`ValidationException` when they call a Vulkan function,
and a non-zero result is returned. The exception message
will always contain the function that failed, as well as
the non-zero result.

## Descriptors
Since descriptor management can be quite verbose in 'raw'
Vulkan, some classes and methods are provided to help.

### Layouts
You can use `boiler.descriptors.createLayout(stack, bindings, name)`
to create a `VkbDescriptorSetLayout`, which is a wrapper around
a `VkDescriptorSetLayout`. You can use
`boiler.descriptors.binding(...)` to slightly reduce the
amount of code needed to populate the `bindings`.

### Pools
You can call the `createPool(maxSets, flags, name)` method of
a `VkbDescriptorSetLayout` to create a
`HomogeneousDescriptorPool` that can allocate exactly
`maxSets` descriptor sets from the layout. The pool has an
`allocate(amount)` method that will allocate `amount`
descriptor sets from the pool. 

The limitation of `HomogeneousDescriptorPool` is that it
supports only 1 descriptor set layout. If you want to
allocate descriptor sets from different layouts from the
same pool, you can use `SharedDescriptorPool` instead.

Alternatively, you can use the 'raw'
`boiler.descriptors.allocate(vkDescriptorPool, name, vkDescriptorSetLayouts...)`.

### Banks
Descriptor 'banks' are wrapped descriptor pools from which
you can borrow and return descriptor sets. There are 2 types
of descriptor banks:
- `FixedDescriptorBank`s wrap only 1 descriptor pool, and you
can only borrow a predefined number of descriptor sets from it
at the same time.
- `GrowingDescriptorBank`s will create more descriptor pools
when needed. You can borrow as many descriptor sets as you
want from such a bank.

You can create both classes using their public constructor.
Both classes are thread-safe and have the same methods:
`borrowDescriptorSet`, `returnDescriptorSet`, and `destroy`.
Also, just like `HomogeneousDescriptorPool`, they only
support 1 descriptor set layout.

### Updating
`vk-boiler` also provides some methods that reduce the
boilerplate code needed for updating descriptor sets.
- You can use 
`boiler.descriptors.bufferInfo(stack, bufferRanges)`
to create a buffer of `VkDescriptorBufferInfo` structs that
can conveniently be passed to 
`VkWriteDescriptorSet.pBufferInfo`.
- You can use `boiler.descriptors.writeBuffer(...)` to
populate a `VkWriteDescriptorSet` that writes a buffer,
in 1 line of code.
- You can use `boiler.descriptors.writeImage(...)` to
populate a `VkWriteDescriptorSet` that writes an image.
You will still need to create the `VkDescriptorImageInfo`(s)
yourself though.

## Images
Almost all applications need to use images, but creating them
in raw Vulkan can be quite verbose. Therefor, several methods
are provided to create them using less code.

### VkbImage
The `VkbImage` class is a simple wrapper around a `VkImage`
that stores some additional information, such as the size.
It also has an optional `VkImageView` and `VmaAllocation`.
Many methods of `vk-boiler` take `VkbImage`s as parameter
rather than raw `VkImage`s because this additional information
allows such methods to be implemented with fewer parameters.

### Creating images
You can use the `ImageBuilder` class to easily create
`VkImage`s. By default, it will also create a
corresponding `VkImageView` and VMA allocation.

### Creating image views
To create an image view for an existing image, you can use
`boiler.images.createView(...)` or
`boiler.images.createSimpleView(...)`. The latter method
simply calls the former method, but assumes some default
parameters.

Note that `ImageBuilder` will also create image views by
default, so you usually only need `createView` to create
swapchain image views.

### Creating framebuffers
You can use `boiler.images.createFramebuffer(...)` to create
framebuffers. Note however that I would recommend to use
dynamic rendering, which avoids the need for framebuffers.

### Creating samplers
You can use `boiler.images.createSampler(...)` or
`boiler.images.createSimpleSampler(...)` to create samplers.
`createSimpleSampler(...)` will simply call `createSampler(...)`,
but it assumes some default parameter values.

### Subresource ranges and layers
Several Vulkan command buffer functions require you to fill in
a `VkImageSubresourceRange` or `VkImageSubresourceLayers`
struct, requiring you to fill in the mip levels and array
layers every single time, even though most images just have 1
of each. In such cases, you can use
`boiler.images.subresourceRange(stack?, range?, aspectMask)`
or `boiler.images.subresourceLayers(layers, aspectMask)` to
fill them in with just 1 line of code.

### Choosing a depth-stencil format
Any Vulkan application that wants a depth test will need to
create depth images, and therefor choose a depth-stencil
format. The
`boiler.images.chooseDepthStencilFormat(formats...)`
method can be used to choose the first format in `formats`
that is supported.

## Pipelines
Creating pipelines, especially graphics pipelines, takes a
ridiculous number of lines of code in raw Vulkan. This really
needs to be trimmed!

### Creating layouts
You can use
`boiler.pipelines.createLayout(pushConstants, name, descriptorSetLayouts...)`
to create a pipeline layout.

### Creating shader modules
You can use
`boiler.pipelines.createShaderModule(resourcePath, name)` to
create a shader module whose SPIR-V code is accessed via
`classLoader.getResourceAsStream(resourcePath)`.

Note that you only need this method when you want more than
just a vertex shader and fragment shader in your pipeline,
since you can otherwise use
`pipelineBuilder.simpleShaderStages(...)` instead.

### Creating compute pipelines
You can use
`boiler.pipelines.createComputePipeline(layout, resourcePath, name)`
to create a compute pipeline with the given pipeline layout,
and whose SPIR-V code is accessed via
`classLoader.getResourceAsStream(resourcePath)`.

### Creating graphics pipelines
You can use the `GraphicsPipelineBuilder` class to reduce the
amount of code needed to create a graphics pipeline. It is
basically a wrapper around a `VkGraphicsPipelineCreateInfo`.
You can create an instance using its public constructor.

This class has many methods to populate the createInfo, but
the createInfo struct is also accessible as public field.
When you want to do something that is not covered by the
methods, you will need to edit this struct yourself.

#### Construction
The `GraphicsPipelineBuilder` class has 2 constructors:
- The `(BoilerInstance, MemoryStack)` constructor will create
a new zero-initialized `VkGraphicsPipelineCreateInfo` struct,
and set its `sType` to
`VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO`. Since its
zero-initialized, the `pNext` and `flags` will be 0.
- The `(VkGraphicsPipelineCreateInfo, BoilerInstance, MemoryStack)`
constructor will wrap an existing createInfo, without modifying
it during the constructor. Thus, the `sType`, `pNext`, and
`flags` are unchanged.

#### Stages
You can use the `shaderStages(...)` or `simpleShaderStages(...)`
methods to populate the `stageCount` and `pStages`. The
`simpleShaderStages(...)` is the simplest, but only supports
a vertex shader and a fragment shader.

If you need more shader stages, you need to use the general
`shaderStages(...)` method, which is a bit more verbose. The
`boiler.pipelines.createShaderModule(...)` method could be
useful in this case.

#### Vertex input state
If your pipeline doesn't need any vertex input state (e.g.
because you're doing vertex pulling), you can use the
`noVertexInput()` method. If you *do* need vertex input, you
will have to populate the `pVertexInputState` of the
createInfo yourself.

#### Input assembly state
If your pipeline uses triangle lists as input assembly, you
can use the `simpleInputAssembly()` method to populate the
`pInputAssemblyState`. If you want another input assembly, you
will have to populate the `pInputAssemblyState` property
yourself.

#### Tessellation
The `GraphicsPipelineBuilder` class doesn't support any methods
to help with the `pTessellationState`. If you want to use
tessellation shaders, you will have to populate it yourself.

#### Viewport state
If you want to postpone the viewport/scissor size selection
to command buffer recording, you should use the
`dynamicViewports(amount)` method. If you want to specify the
size of the viewport and scissor right now, you should use the
`fixedViewport(width, height)` method.

#### Rasterization state
If you want a 'simple' pipeline rasterization state, you can
use the `simpleRasterizationState(cullMode)` method. This will
choose `VK_POLYGON_MODE_FILL` and
`VK_FRONT_FACE_COUNTER_CLOCKWISE`. It won't have any depth bias
or depth clamp, and it won't enable rasterizer discard. If you
need a different rasterization state, you will have to modify
the `pRasterizationState` yourself.

#### Multisample state
If you don't want to use multisampling, you can use the
`noMultisampling()` method. If you *do* need multisampling, you
will have to populate the `pMultisampleState` property yourself.

#### Depth-stencil state
If you don't want to use a depth test or stencil test, you can
use the `noDepthStencil()` method. If you want to use a 'simple'
depth test, you can use the `simpleDepthTest(compareOp)`
method. This will enable the depth test and depth write, but
not the depth bounds test.

If you want to use a 'complex' depth test, or if you want to use
a stencil test, you will need to populate the
`pDepthStencilState` yourself.

#### Color blend state
If you don't want the pipeline to support color blending, you
can use the `noColorBlending(attachmentCount)` method. If you
want the pipeline to support 'standard' color blending, you
can use the `simpleColorBlending(attachmentCount)` method.
For anything special, you will need to modify the
`pColorBlendState` yourself.

#### Dynamic state
If you want to use dynamic states, you can use the
`dynamicStates(states...)` method. Note that you need to use
`dynamicStates(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR)`
if you also use `dynamicViewports(amount)`.

#### Layout
You need to populate the `layout` of the createInfo yourself,
but you can use `boiler.pipelines.createLayout(...)` to create
this layout using fewer lines of code.

#### Render pass
If you use dynamic rendering, you can use the
`dynamicRendering(...)` method, which will chain a
`VkPipelineRenderingCreateInfo` to the `pNext` chain.

If you don't use dynamic rendering, you need to populate the
`renderPass` and `subpass` yourself.

#### Base pipeline
If you want to use a base pipeline, you need to populate the
`basePipelineHandle` or `basePipelineIndex` yourself.

#### Pipeline cache
If you want the `build(name)` method to pass a `pipelineCache`
to `vkCreateGraphicsPipelines`, you should change the value of
the `pipelineCache` field of the `GraphicsPipelineBuilder`.

#### Building
To build the pipeline, you can use the `build(name)` method.
This will call `vkCreateGraphicsPipelines`, assign a debug
name to the created pipeline handle (when validation is
enabled), and destroy the shader modules (if you used
`simpleShaderStages(...)`).

If you want to create multiple graphics pipelines at the same
time, you need to call `vkCreateGraphicsPipelines` yourself.

## Queues

### Queue families
You can use `boiler.queueFamilies` to get access to the queue
families for which queues were created. For instance, you can
use `boiler.queueFamilies.graphics` to access the graphics
queue family. Note that `boiler.queueFamilies.graphics` is
usually the same as `boiler.queueFamilies.compute`, especially
when you did *not* chain a custom `queueFamilyMapper` to the
`BoilerBuilder`.

Every queue family has an index, and a list of queues that were
created during device creation. For convenience, each queue
family also has a `first()` method that simply returns the first
queue.

### The VkbQueue class
Each queue family has a list of `VkbQueue`s, which are wrappers
around `VkQueue`s. They have `submit` methods and a `present`
method. All methods are **synchronized**, which ensures that
all access to `VkQueue`s will be correctly externally
synchronized, as long as you only do queue submissions via these
methods. The `submit` methods have quite some parameters, so
you should read their doc comments for more information.
Alternatively, you can take 
[HelloTriangle](../samples/src/main/java/com/github/knokko/boiler/samples/HelloTriangle.java)
as example.

## Synchronization
Synchronization in raw Vulkan can be quite messy and verbose.
Therefor, several abstractions and helper methods are provided.

### VkbFence
The `VkbFence` class is a wrapper class for a `VkFence`, plus
some time tracking. This time tracking makes it possible to
await old fence submissions, even after the fence has already
been reset. It is also possible to 'signal' such fences from
the CPU (without an actual queue submission). This abstraction
is also extensively used in the swapchain management system of
`vk-boiler`. You should check out the doc comments for more
information, or the
[unit tests](../src/test/java/com/github/knokko/boiler/synchronization/TestFenceSubmission.java)
.

### Fence bank
You can use the `FenceBank` accessible from
`boiler.sync.fenceBank` to borrow (and return) `VkbFence`s.
If you use this, you will only create fences when really needed,
and reduce the risk of running into 'driver limits' when you
frequency create and destroy fences.

### Semaphore bank
Next to the fence bank, there is also the
`boiler.sync.semaphoreBank` from which you can borrow and
return **binary** semaphores. Note that it is important that
you do **not** return semaphores that are still pending
(just like you shouldn't destroy them while they are pending).

### Timeline semaphores
You can use `boiler.sync.createTimelineSemaphore(value, name)`
to create a `VkbTimelineSemaphore`, which is a simple wrapper
around a timeline `VkSemaphore`. This wrapper class provides
the methods `waitUntil(value)`, `getValue()`, and
`setValue(newValue)`, which only require 1 line of code to call.

### Resource usage
The `ResourceUsage` record is needed in several pipeline
barrier methods of the `CommandRecorder` class. It's basically
an `(imageUsage, accessMask, stageMask)` tuple. The record
has some constants and static methods that can be used to get
commonly-used instances.

## Utilities
The following utilities are/were made for `vk-boiler` internally,
but you are free to use it in applications as well.

### Math functions
- You can use `BoilerMath.nextMultipleOf(value, alignment)`
to find the smallest integer multiple of `alignment` that is
greater than or equal to `value`. As the name suggests, this
can be useful when working with buffer alignment requirements.

### Set functions
- You can use `CollectionHelper.createSet(a, b, c)` to create
a `Set` containing the elements `a`, `b`, and `c`.
- You can use `CollectionHelper.decodeStringSet(PointerBuffer)`
to decode a list of C-strings, and turn it into a `Set<String>`.
This is for instance useful for parsing `ppEnabledLayerNames`.
- You can use `CollectionHelper.encodeStringSet(set, stack)`
to encode the given set into a `PointerBuffer` that is allocated
on the given `MemoryStack`. This is for instance useful for
populating `ppEnabledLayerNames`.

### Color packing
The `ColorPacker` class provides a `rgb` and `argb` method that
can be used to pack 8-bit red, green, blue, and alpha components
into a (32-bit) `int`. It also provides methods to extract
the red, green, blue, and alpha components from the `int`s that
it has produced.

### pNext chains
You can use `NextChain.findAddress(pNext, sType)` to find the
memory address of the struct with the given `sType` in the
chain that starts at `pNext`. This is occasionally useful in
unit tests.

### Constant names
You can use `ReflectionHelper.getIntConstantName` to search for
an `int` constant in a class that has the given value, and the
right prefix and/or suffix. This is used to figure out the
name of Vulkan error codes (like `VK_ERROR_DEVICE_LOST`).

## Virtual reality
`vk-boiler` supports OpenXR integration. OpenXR is a Khronos
standard (just like Vulkan) for virtual reality and augmented
reality. OpenXR applications can choose from several rendering
APIs, where Vulkan is just 1 of them. Unfortunately, this
integration between Vulkan and OpenXR is very verbose. That's
why `vk-boiler` helps during instance creation, and provides
many helper functions.

### Enabling virtual reality support
First of all, you need to chain `.xr(...)` to the `BoilerBuilder`
(see [the initialization documentation](./initialization.md))
for more information. Obviously, this will only work if a
virtual reality headset is connected to the computer that runs
the application (or has some emulator).

### Creating the OpenXR session
After creating the `BoilerInstance`, the next step in a virtual
reality application is typically creating the `XrSession`. You
can do this using `boiler.xr().createSession(...)`. This will
return a `VkbSession` (which wraps an `XrSession`). The
`VkbSession` class provides some potentially useful instance
methods, which will be explained later.

### Creating the OpenXR swapchain
Before creating the swapchain, you need to choose a swapchain
image format. You can use
`session.chooseSwapchainFormat(preferredFormats...)`
for this, but you can also call `xrEnumerateSwapchainFormats`
yourself.

You also need to choose the size of the swapchain. You can use
`boiler.xr().getViewConfigurationViews(...)` to query the
minimum, maximum, and recommended swapchain size. Alternatively,
you can call `xrEnumerateViewConfigurationViews` yourself.

To create the swapchain, you can use
`session.createSwapchain(...)`, or call `xrCreateSwapchain`
yourself.

You will probably also want to create swapchain image views
and depth images. Since this is regular Vulkan stuff, you can
use the methods of `boiler.images`.

### Creating OpenXR actions
You can use `boiler.xr().actions.createSet(...)` to create an
`XrActionSet`. This method will simply call `xrCreateActionSet`
under the hood, but using this should take fewer lines of code.

You can use `boiler.xr().actions.getPath(stack, "/path/string")`
to convert a path string to an `XrPath`. This method will simply
call `xrStringToPath`, but should be slightly more convenient.

You can use `boiler.xr().actions.create(...)` to create an
`XrAction` without subactions, and you can use
`boiler.xr().actions.createWithSubactions` to create an
`XrAction` with subactions. Both methods will just call
`xrCreateAction`, but should be more convenient for you.

### Suggesting interaction profile bindings
After creating actions, you should suggest bindings for the
controllers you intend to support. Since using raw
`XrActionSuggestedBinding`s is quite verbose, I made a
`SuggestedBindingsBuilder` class to lighten the work.

Start by creating an instance, which should look like this:
```java
var bindingsBuilder = new SuggestedBindingsBuilder(
	boiler.xr(), 
	"/interaction_profiles/khr/simple_controller"
);
```
The second parameter is the interaction profile for which
you want to suggest bindings.

Then add some suggested bindings, like this:
```java
bindingsBuilder.add(
	handPoseAction, "/user/hand/right/input/aim/pose"
);
bindingsBuilder.add(
	handClickAction, "/user/hand/left/input/select/click"
);
```
The first parameter is the `XrAction` to bind, and the
second parameter is the path to which you want to bind it.
Once you have suggested all bindings, call
`bindingsBuilder.finish()` (which will actually call
`xrSuggestInteractionProfileBindings`).

### Creating OpenXR spaces
You can call `session.createReferenceSpace(...)` to create a
reference space of the given `XrReferenceSpaceType`. This
is just a convenience method that will call
`xrCreateReferenceSpace` under the hood.

You can call `session.createActionSpace(...)` to create
an action space for the given (sub)action. This is also a
convenience method, and will simply call `xrCreateActionSpace`
under the hood.

Note that both methods will use the identity pose as
`poseInReferenceSpace`/`poseInActionSpace`.

### Attaching action sets to the session
You can call `session.attach(stack, actionSet)` to attach a
single `XrActionSet` to the `XrSession` (using
`xrAttachSessionActionSets`). If you wish to attach multiple
action sets at the same time, you will have to call
`xrAttachSessionActionSets` yourself.

### The session loop
Once all action sets and Vulkan resources are set up, it's time
to start the session loop. Managing a session loop is a lot of
work (code) that shouldn't depend much on the application.
The `SessionLoop` class was made to lighten this work for
applications. It is an abstract class that handles most of the
work, but you need to implement several methods:

#### Creating the projection matrix
You need to implement the method
`Matrix4f createProjectionMatrix(XrFovf fov)`.
You could easily do this by returning
`xr.createProjectionMatrix(fov, nearPlane, farPlane)`,
but you can also create a more complicated projection matrix.

#### Choosing the active action set
You need to implement the method
`XrActionSet[] chooseActiveActionSets()`. If your application
only has 1 action set, you could attach it before starting
the session loop, and always return it.

If your application actually switches action sets, it becomes
a bit more complicated. When you want to change the active
action set, you will need to *attach* the new action set during
this method, and then return it. If you want to keep using the
same action set as the last call, just return that same
action set(s).

#### The update method
You need to implement the method `void update()`, which will
be called during every iteration of the session loop, always
after polling and handling events. You can do whatever you
want in this method, and are allowed to leave the method body
empty.

#### Handling events
You need to implement the method
`void handleEvent(XrEventDataBuffer event)`. This method will
be called for each event that is polled with `xrPollEvent`.
Just like in the update method, you can do whatever you want
in this method, including leaving the method body empty.

Note that the `SessionLoop` class will automatically handle
`XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED` events before calling
`handleEvent`. You may listen to session state changes, but you
don't **need** to.

#### Waiting for render resources
You need to implement the method
`void waitForRenderResources(MemoryStack stack)`. You should use
this method to wait for render resources (like command buffers
and fences) that you will need during the *next* frame.

This method will only be called during session loop iterations
where the session state is either `SYNCHRONIZED`, `VISIBLE`,
`FOCUSSED`, or `READY`. Note that this does *not* necessarily
mean that a frame will be rendered during this iteration.

#### Recording render commands
You need to implement the method
```java
void recordRenderCommands(
	MemoryStack stack, XrFrameState frameState, int swapchainImageIndex, Matrix4f[] cameraMatrices
)
```
During this method, you should record all the command buffers
that you intend to submit for this frame. This method will be
called right after `xrAcquireSwapchainImage` and
`xrSyncActions`, which means that the next swapchain image is
known, but that it's not yet ready.

Therefor, you may (and should) record command buffers that use
the swapchain image, but you may not submit them yet. You may
submit any command buffers that do **not** need the swapchain
image.

Since this method is called right after `xrSyncActions`, it is
also the best moment to query actions.

#### Submitting the render commands
You need to implement the method `void submitRenderCommands()`.
During this method, you should submit the command buffers that
you recorded during `recordRenderCommands`. This method will be
called right after `xrWaitSwapchainImage` and right before
`xrReleaseSwapchainImage`.

### Querying action states
You can use `session.prepareActionState(...)` or
`session.prepareSubactionState(...)` to allocate an
`XrActionStateGetInfo` onto the stack, using just 1 line of code.
Then, you can pass this `XrActionStateGetInfo` to one of the
following methods:
- `session.getBooleanAction(...)`
- `session.getFloatAction(...)`
- `session.getVectorAction(...)`
- `session.getPoseAction(...)`

Each of these methods will simply call
`xrGetActionStateXXX` using the `XrActionStateGetInfo`, but it
should take you slightly less code.

### Querying action spaces
You can use `boiler.xr().locateSpace(...)` to locate an
action space. This will call `xrLocateSpace`, and convert the
queried position and orientation to a JOML `Vector3f` and
`Quaternionf`. You can additionally call the `createMatrix()`
method of the result to convert them to a `Matrix4f`.