# Initializing vk-boiler
To get started with `vk-boiler`, you need to create a `BoilerInstance`.
You can either create the `BoilerInstance` via its constructor,
which is **not** recommended, or you can create it via `BoilerBuilder`,
which is the recommended and easiest way.

## Basic usage of `BoilerBuilder`
```java
var boiler = new BoilderBuilder(
		VK_API_VERSION_1_2, "YourApplicationName", 1
).build();
```
Using the above code snippet would give you a simple `BoilerInstance`
using Vulkan 1.2, with a small number of extensions and features.
The API version is quite important since it filters out physical
devices that don't support the API version, but the other two
parameters are almost always ignored by the graphics driver
(unless your game is very popular).
Some considerations for the API version:
- Pretty much any desktop device with Vulkan support will support
at least Vulkan 1.2 (unless the drivers are horribly outdated).
- Pretty much any *modern* desktop device also supports Vulkan 1.3,
so picking 1.2 is better if you want more compatibility.
- I have never tried LWJGL (nor vk-boiler) on mobile devices, so
I would not consider those unless you have concrete plans.
- Targeting Vulkan 1.0 or 1.1 is only useful if you intend
to support horribly outdated drivers

## Instance creation properties
### Validation
To enable the validation layers, you should chain `.validation()`
to the `BoilerBuilder` before calling `.build()`. This will
enable basic validation, synchronization validation, and best
practices validation. Furthermore, if the API version is 1.1 or
later, it will also enable GPU-assisted validation.

Note: a `MissingVulkanLayerException` will be thrown during the
`.build()` if the target machine doesn't have validation layers.
Therefor, you should only use this method during development.

If you want more control, you can use
`.validation(new ValidationFeatures(...))` instead.

For more advanced validation, you should use
[vk-config](https://github.com/LunarG/VulkanTools/blob/main/vkconfig/README.md)
instead.

If you additionally chain `.forbidValidationErrors()`, an exception
will be thrown whenever a validation error occurs. This can be
useful for tracking down which function call caused a validation
error, and is also very convenient for unit tests.

### (Instance) layers
If you want to enable (instance) layers, you can chain
`.desiredVkLayers(...)` or `.requiredVkLayers(...)`. Both methods will
enable the layers when the target machine supports them. Their
difference is that `desiredVkLayers` will ignore unsupported layers,
whereas `requiredVkLayers` will throw a `MissingVulkanLayerException`
when at least 1 layer is not supported.

When you have also chained `.validation()`,
`VK_LAYER_KHRONOS_validation` will automatically be added to the
required layers, so you do **not** need to add it to
`.requiredVkLayers` yourself.

You can chain `.apiDump()` to require the LunarG api dump layer.
This method is just a shorthand for adding the api dump layer to the
`requiredVkLayers`. This method exists because this layer is commonly
used (at least by me).

### Instance extensions
If you want to enable instance extensions, you can chain
`.desiredVkInstanceExtensions(...)` or
`.requiredVkInstanceExtensions(...)`. Both methods will enable the
instance extensions if they are supported by the target machine.
The difference is that `desiredVkInstanceExtensions` will ignore
unsupported extensions, whereas `requiredVkInstanceExceptions`
will throw a `MissingVulkanExtensionException` when at least
1 of them is not supported by the target machine.

When you have also chained `.validation()`, the builder will
automatically enable the debug utils and validation features
extensions as well, so you do **not** need to think about this.

### Engine name/version
You can use `.engine(name, version)` to propagate the given name
and version to `VkApplicationInfo.engineName` and
`VkApplicationInfo.engineVersion`.

### Instance creation callbacks
If the above methods are not powerful enough for you, you can use
`.beforeInstanceCreation(callback)` and
`.vkInstanceCreator(callback)` to get complete control over
instance creation.

If you simply need to modify the `VkInstanceCreateInfo`, you should
use `.beforeInstanceCreation`.

If you need to call a custom function instead of `vkCreateInstance`,
you need to use `.vkInstanceCreator`.

## Physical device selection properties
When the target machine supports multiple physical devices,
the builder will choose 1, depending on your requirements,
preferences, and some defaults.

Note: if not a single physical device on the target machine
satisfies all requirements, a `NoVkPhysicalDeviceException` will
be thrown during `.build()`. Therefor, adding ambitious requirements
will reduce the number of computers that can run your application.

Hint: you can chain `.printDeviceRejectionInfo()` to figure out
why each device was filtered out.

### Required device extensions
You can chain `.requiredDeviceExtensions(...)` to add required
device extensions:
- Any device that doesn't support these extensions will be
filtered out.
- If device selection succeeds, all of them will be enabled.

### Required device features
You can chain `.requiredFeaturesXX(callback)` to add required
Vulkan XX features. This will filter out all devices for which
the `callback` return `false`, but it will **not** automatically
enable the features: you need to use `.featurePickerXX` for that.

### Extra device requirements
For more complicated device filtering, you can chain
`.extraDeviceRequirements(callback)`. The callback will get the
candidate physical device and the window surfaces as input,
and can query whatever it needs to make its decision.

### Choosing between physical devices
When multiple physical devices satisfy all the requirements, 1 of
them needs to be chosen. The default device selector will prefer
discrete GPUs over any other GPUs, and prefer integrated GPUs over
any non-discrete GPUs. You can change this behavior by chaining
`.physicalDeviceSelector(callback)`.

Note: instead of supplying a callback, you can also supply an
instance of `SimpleDeviceSelector`, which will simply choose the
device based on its device type. For instance, you can use
```java
builder.physicalDeviceSelector(new SimpleDeviceSelector(
		VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU,
		VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU
))
```
to prefer integrated GPUs over discrete GPUs.

## Device creation
Once a physical device has been selected, it's time to create the
(logical) device. You can control, among others, which extensions
and features are enabled.

### Device extensions
First of all, all device extensions that were passed to
`.requiredDeviceExtensions` will automatically be enabled.
Furthermore, you can chain `.desiredDeviceExtensions` to enable
device extensions, if and only if they are supported by the
physical device. Any unsupported device extensions will be
ignored.

### Device features
To enable Vulkan XX device features, you need to chain
`.featurePickerXX(callback)`. Given the *supported features*, the
callback can enable features in the *toEnable* struct.

If you already used `.requiredFeaturesXX` to ensure that the
features are supported by the selected device, you can safely
assume that they are supported, without the need to check it again.

### Queue families
By default, the builder will create the minimum number of device
queue families that it needs to function, and it will create
exactly 1 queue per queue family. It requires:
- A graphics queue
- A compute queue
- For each window (if any), a queue that can present to its surface
- When video en/decoding extensions are enabled, a queue for video
en/decoding

In almost all cases, it will simply create 1 queue in a queue family
that supports graphics and compute, and can present to each
window surface. In hypothetical devices, it may need to use more
queue families.

If you don't like this behavior (e.g. because you want more queues
or a distinct compute queue family), you can chain
`.queueFamilyMapper(callback)`, and figure out all the logic
yourself. I might add more `QueueFamilyMapper` implementations in
the future or upon request.

### Device creation callbacks
If the above methods are not powerful enough, you can use
`.beforeDeviceCreation(callback)` or `.vkDeviceCreator(callback)`
to get more control.

You can use `.beforeDeviceCreation` to modify the
`VkDeviceCreateInfo` before the device is created. You could e.g.
append some structures to its `pNext` chain.

You can use `.vkDeviceCreator` to use a different function to
create the `VkDevice`. The default device creator will simply
call `vkCreateDevice`, but you can use this callback to do something
else instead.

## Window creation
Since most Vulkan applications want to create a window
(and present to it), the builder provides methods to make window
creation a lot easier.

### Initializing GLFW
By default, the builder will initialize GLFW (call `glfwInit()`)
if it is going to create at least 1 window. You can prevent this
by chaining `.dontInitGLFW`.

### Adding windows
You can add a window by chaining `.addWindow(windowBuilder)`,
where the `WindowBuilder` is a builder class by itself. You should
create a `WindowBuilder` using its constructor, and optionally
chain some methods behind it.

The constructor requires only the initial `width` and `height`
of the window (in pixels), and the image usage flags of the
swapchain images that will be created for it.

#### Title
You can chain `.title(...)` to change the window title. By
default, it will be your application name (that you passed to
the constructor of the `BoilerBuilder`).

#### Hide until first frame
You can chain `.hideUntilFirstFrame()` to hide the window until
the first image has been presented (at least, until the first
call to `vkQueuePresentKHR`). This prevents people from seeing
black/white/garbage content right after the window is opened.

#### Composite alpha picker
You can chain `.compositeAlphaPicker(callback)` to change the
composite alpha picker. The default picker will try to simply
make the window opaque, but you can override this to e.g. create
transparent or translucent windows. Hint: you can use
`SimpleCompositeAlphaPicker` to avoid some boilerplate code.

#### Surface format picker
You can chain `.surfaceFormatPicker(callback)` to change the
surface format picker. The default picker will try to pick
`R8G8B8A8_SRGB` or `B8G8R8A8_SRGB` with a non-linear color space.
If you don't want this for some reason (e.g. you want to have
a `UNORM` format and do the SRGB conversion yourself), you can
chain this method and roll your own picker. Hint: using the
`SimpleSurfaceFormatPicker` is going to be easier, if it's powerful
enough for you.

#### Relevant present modes
When the `VK_EXT_swapchain_maintenance1` extension is enabled,
it is possible to create swapchains that can switch their
present mode (without recreating it), under the right circumstances.
When you chain `.presentModes(...)`, the swapchain manager will try
to make the swapchains compatible with these present modes.

Note: the swapchain manager will also try to make swapchains
compatible with any present mode that you have used before, so
chaining this method will only speed up the first couple of present
mode switches.

#### Using an existing window
When you have special window requirements that are not covered by
the methods above, you can create the GLFW window yourself, and
chain `.glfwWindow(yourWindow)`.

If you do this, the builder will simply use this window rather
than creating a new window itself. This also means that the
`width`, `height` and `title` of the `WindowBuilder` will be
ignored. The builder will still create a surface for your window.

#### Adding a window creation callback
When you create exactly 1 window, you can retrieve it using
`boilerInstance.window()`. When you create multiple windows, you
can **not** do this because the `BoilerInstance` doesn't know
which window it should return.

To work around this problem, you need to chain a
`.callback(someCallback)` to each window builder. This callback
will be called after the corresponding window and `BoilerInstance`
have been created. You can e.g. let the callback set an instance
field of one of your classes, or let it write to a fixed index into
a `VkbWindow[]`.

#### Adding windows after creating the `BoilerInstance`
It is also possible to add windows after the `BoilerInstance`
has already been created. You can do this by calling
`boilerInstance.addWindow(windowBuilder)`.

This method is however not preferred because the queue families
of the `BoilerInstance` are fixed at this point. In the
(admittedly hypothetical) case that:
- The graphics queue family of the `BoilerInstance` can
**not** present to the surface of the window.
- There is another queue family of the physical device of the
`BoilerInstance` that **can** present to this surface.
- The builder did not create a queue for this queue family because
it didn't know that it needed to be able to present to this new
window.

The window creation will fail because the `BoilerInstance`
doesn't have a queue capable of presenting to the window.

## Virtual reality
`vk-boiler` also supports OpenXR (virtual/augmented reality).
Initialization of a Vulkan OpenXR application normally takes
hundreds of lines of boilerplate code, but `vk-boiler` reduces
this substantially.

To enable OpenXR integration, chain `.xr(xrBuilder)` to the
`BoilerBuilder`. In its simplest form, the initialization effort
is no more work than `.xr(new BoilerXrBuilder())`, but you can
chain method calls to the `BoilerXrBuilder` to customize it.

### OpenXR layers
To enable OpenXR layers, you can chain `.desiredLayers(...)`
or `.requiredLayers(...)` to the `BoilerXrBuilder`. Both will
cause the layers to be enabled if they are supported by the OpenXR
runtime.

The difference is that `.requiredLayers` will cause a
`MissingOpenXrLayerException` to be thrown when at least 1 layer
is not supported by the OpenXR runtime, whereas `.desiredLayers`
will ignore layers that are not supported by the OpenXR runtime.

If you chained `.validation()` on the `BoilerBuilder`, the
`XR_APILAYER_LUNARG_core_validation` layer will automatically
be added to the required layers.

### OpenXR extensions
To enable OpenXR extensions, you can chain `.desiredExtensions(...)`
or `.requiredExtensions(...)` to the `BoilerXrBuilder`. Both
will cause the extensions to be enabled if they are supported by
the OpenXR runtime.

The difference is that `.requiredExtensions` will cause a
`MissingOpenXrExtensionException` to be thrown when at least 1
extension is not supported by the OpenXR runtime, whereas
`.desiredExtensions` will ignore unsupported extensions.

The `XR_KHR_vulkan_enable2` extension will automatically be added
to the required extensions because it is needed by the builder.

### OpenXR form factor
By default, the xr builder will use the form factor
`XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY`. If you want to use a
different form factor, you can chain `.formFactor(yourFormFactor)`
to the xr builder.

## Miscellaneous
### Default timeout
Some Vulkan functions (like `vkWaitForFences` and
`vkAcquireNextImageKHR`) have some timeout parameter. Every
`BoilerInstance` will have a default value that its children will
use when you don't specify one. You can change the default value
by chaining `.defaultTimeout(nanoseconds)` to the builder. The
default value is 1 second.

### Dynamic rendering
You can chain `.dynamicRendering()` to the builder to
- Filter out all physical devices that don't support dynamic
rendering.
- Enable dynamic rendering on the `VkDevice` that will be created.

This method is not really required since you could achieve the
same effect by using the device selection methods and the device
creation methods. However, since dynamic rendering is pretty
popular, I added this convenience method to lighten the work.