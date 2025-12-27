module vk.boiler.main {
	requires static java.desktop;
	requires static org.joml;
	requires org.lwjgl;
	requires static org.lwjgl.glfw;
	requires static org.lwjgl.openxr;
	requires static org.lwjgl.sdl;
	requires static org.lwjgl.vma;
	requires org.lwjgl.vulkan;

	exports com.github.knokko.boiler;
	exports com.github.knokko.boiler.buffers;
	exports com.github.knokko.boiler.builders;
	exports com.github.knokko.boiler.builders.device;
	exports com.github.knokko.boiler.builders.instance;
	exports com.github.knokko.boiler.builders.queue;
	exports com.github.knokko.boiler.builders.window;
	exports com.github.knokko.boiler.builders.xr;
	exports com.github.knokko.boiler.commands;
	exports com.github.knokko.boiler.culling;
	exports com.github.knokko.boiler.debug;
	exports com.github.knokko.boiler.descriptors;
	exports com.github.knokko.boiler.exceptions;
	exports com.github.knokko.boiler.images;
	exports com.github.knokko.boiler.memory;
	exports com.github.knokko.boiler.memory.callbacks;
	exports com.github.knokko.boiler.pipelines;
	exports com.github.knokko.boiler.queues;
	exports com.github.knokko.boiler.synchronization;
	exports com.github.knokko.boiler.utilities;
	exports com.github.knokko.boiler.window;
	exports com.github.knokko.boiler.xr;
}
