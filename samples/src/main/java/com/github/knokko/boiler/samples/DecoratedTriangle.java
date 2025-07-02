package com.github.knokko.boiler.samples;

import com.github.knokko.boiler.BoilerInstance;
import com.github.knokko.boiler.buffers.MappedVkbBuffer;
import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.builders.WindowBuilder;
import com.github.knokko.boiler.commands.CommandRecorder;
import com.github.knokko.boiler.memory.MemoryBlock;
import com.github.knokko.boiler.memory.MemoryCombiner;
import com.github.knokko.boiler.pipelines.GraphicsPipelineBuilder;
import com.github.knokko.boiler.synchronization.ResourceUsage;
import com.github.knokko.boiler.window.AcquiredImage;
import com.github.knokko.boiler.window.SimpleWindowRenderLoop;
import com.github.knokko.boiler.window.VkbWindow;
import com.github.knokko.boiler.window.WindowEventLoop;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.sdl.SDL_MouseMotionEvent;
import org.lwjgl.sdl.SDL_Point;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;

import java.nio.FloatBuffer;

import static com.github.knokko.boiler.exceptions.SDLFailureException.assertSdlSuccess;
import static com.github.knokko.boiler.utilities.ColorPacker.*;
import static java.lang.Math.abs;
import static org.lwjgl.sdl.SDLEvents.*;
import static org.lwjgl.sdl.SDLMouse.SDL_GetMouseState;
import static org.lwjgl.sdl.SDLVideo.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_FIFO_KHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.VK_API_VERSION_1_2;

public class DecoratedTriangle extends SimpleWindowRenderLoop {

	private static final int NUM_TRIANGLES = 1 + // Main triangle
			3 + // Close icon, maximize icon, and minimize icon
			8; // 2 triangles for each border

	private static final int BORDER_WIDTH = 10;
	private static final int BUTTON_OFFSET = 5;

	private MemoryBlock memory;
	private MappedVkbBuffer vertexBuffer;
	private long pipelineLayout, graphicsPipeline;

	private int mouseX = -123;
	private int mouseY = -123;

	public DecoratedTriangle(VkbWindow window) {
		super(
				window, 2, false, VK_PRESENT_MODE_FIFO_KHR,
				ResourceUsage.COLOR_ATTACHMENT_WRITE, ResourceUsage.COLOR_ATTACHMENT_WRITE
		);

		assertSdlSuccess(SDL_AddEventWatch((userData, rawEvent) -> {
			int eventType = SDL_Event.ntype(rawEvent);
			if (eventType == SDL_EVENT_MOUSE_MOTION) {
				mouseX = (int) SDL_MouseMotionEvent.nx(rawEvent);
				mouseY = (int) SDL_MouseMotionEvent.ny(rawEvent);
			}
			if (eventType == SDL_EVENT_MOUSE_BUTTON_DOWN) {
				if (isMouseOver(-1)) {
					try (var stack = stackPush()) {
						var event = SDL_Event.calloc(stack);
						event.type(SDL_EVENT_WINDOW_CLOSE_REQUESTED);
						event.window().windowID(SDL_GetWindowID(window.handle));
						SDL_PushEvent(event);
					}
				}
				if (isMouseOver(-2)) {
					if ((SDL_GetWindowFlags(window.handle) & SDL_WINDOW_MAXIMIZED) == 0) SDL_MaximizeWindow(window.handle);
					else SDL_RestoreWindow(window.handle);
				}
				if (isMouseOver(-3)) {
					SDL_MinimizeWindow(window.handle);
				}
			}
			return false;
		}, 0L), "AddEventWatch");
		assertSdlSuccess(SDL_SetWindowHitTest(window.handle, (sdlWindow, rawPoint, userData) -> {
			int x = SDL_Point.nx(rawPoint);
			int y = SDL_Point.ny(rawPoint);
			for (int relative = -3; relative <= -1; relative++) {
				if (isMouseOver(relative, x, y)) return SDL_HITTEST_NORMAL;
			}
			if (y < 50) return SDL_HITTEST_DRAGGABLE;

			if (x < BORDER_WIDTH) {
				if (y > window.getHeight() - BORDER_WIDTH) return SDL_HITTEST_RESIZE_BOTTOMLEFT;
				else return SDL_HITTEST_RESIZE_LEFT;
			}

			if (x > window.getWidth() - BORDER_WIDTH) {
				if (y > window.getHeight() - BORDER_WIDTH) return SDL_HITTEST_RESIZE_BOTTOMRIGHT;
				else return SDL_HITTEST_RESIZE_RIGHT;
			}

			if (y > window.getHeight() - BORDER_WIDTH) return SDL_HITTEST_RESIZE_BOTTOM;

			return SDL_HITTEST_NORMAL;
		}, 0L), "SetWindowHitTest");
	}

	private boolean isMouseOver(int relative, int mouseX, int mouseY) {
		int relativeX = mouseX - (window.getWidth() + relative * 50) - BUTTON_OFFSET;
		int relativeY = mouseY - BUTTON_OFFSET;
		int size = 50 - 2 * BUTTON_OFFSET;
		if (relativeX >= 0 && relativeX < size && relativeY >= 0 && relativeY < size) {
			int symmetricX = abs(relativeX - size / 2);
			return (size / 2 - symmetricX) >= relativeY / 2;
		} else return false;
	}

	private boolean isMouseOver(int relative) {
		return isMouseOver(relative, mouseX, mouseY);
	}

	private void putPoint(FloatBuffer destination, int x, int y, int color) {
		destination.put(2f * x / window.getWidth() - 1f);
		destination.put(2f * y / window.getHeight() - 1f);
		destination.put(normalize(red(color)));
		destination.put(normalize(green(color)));
		destination.put(normalize(blue(color)));
	}

	@Override
	protected void setup(BoilerInstance boiler, MemoryStack stack) {
		super.setup(boiler, stack);
		var combiner = new MemoryCombiner(boiler, "VertexMemory");
		this.vertexBuffer = combiner.addMappedDeviceLocalBuffer(
				20 * 3 * NUM_TRIANGLES, 4, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT
		);
		this.memory = combiner.build(false);

		this.pipelineLayout = boiler.pipelines.createLayout(null, "DrawingLayout");
		var vertexBindings = VkVertexInputBindingDescription.calloc(1, stack);
		vertexBindings.binding(0);
		vertexBindings.stride(Float.BYTES * (2 + 3));
		vertexBindings.inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

		var vertexAttributes = VkVertexInputAttributeDescription.calloc(2, stack);
		var attributePosition = vertexAttributes.get(0);
		attributePosition.location(0);
		attributePosition.binding(0);
		attributePosition.format(VK_FORMAT_R32G32_SFLOAT);
		attributePosition.offset(0);
		var attributeColor = vertexAttributes.get(1);
		attributeColor.location(1);
		attributeColor.binding(0);
		attributeColor.format(VK_FORMAT_R32G32B32_SFLOAT);
		attributeColor.offset(4 * 2);

		var ciVertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack);
		ciVertexInput.sType$Default();
		ciVertexInput.pVertexBindingDescriptions(vertexBindings);
		ciVertexInput.pVertexAttributeDescriptions(vertexAttributes);

		var pipelineBuilder = new GraphicsPipelineBuilder(boiler, stack);
		pipelineBuilder.simpleShaderStages(
				"Triangle", "com/github/knokko/boiler/samples/graphics/",
				"triangle.vert.spv", "triangle.frag.spv"
		);
		pipelineBuilder.ciPipeline.pVertexInputState(ciVertexInput);
		pipelineBuilder.simpleInputAssembly();
		pipelineBuilder.dynamicViewports(1);
		pipelineBuilder.simpleRasterization(VK_CULL_MODE_NONE);
		pipelineBuilder.noMultisampling();
		pipelineBuilder.noDepthStencil();
		pipelineBuilder.noColorBlending(1);
		pipelineBuilder.dynamicStates(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR);
		pipelineBuilder.dynamicRendering(0, VK_FORMAT_UNDEFINED, VK_FORMAT_UNDEFINED, window.surfaceFormat);
		pipelineBuilder.ciPipeline.layout(pipelineLayout);
		this.graphicsPipeline = pipelineBuilder.build("TrianglePipeline");
	}

	@Override
	protected void recordFrame(MemoryStack stack, int frameIndex, CommandRecorder recorder, AcquiredImage acquiredImage, BoilerInstance instance) {
		var colorAttachments = recorder.singleColorRenderingAttachment(
				acquiredImage.image().vkImageView, VK_ATTACHMENT_LOAD_OP_CLEAR,
				VK_ATTACHMENT_STORE_OP_STORE, rgb(20, 120, 180)
		);

		recorder.beginSimpleDynamicRendering(
				acquiredImage.width(), acquiredImage.height(),
				colorAttachments, null, null
		);
		recorder.dynamicViewportAndScissor(acquiredImage.width(), acquiredImage.height());
		vkCmdBindPipeline(recorder.commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicsPipeline);
		recorder.bindVertexBuffers(0, vertexBuffer);
		vkCmdDraw(recorder.commandBuffer, 3 * NUM_TRIANGLES, 1, 0, 0);
		recorder.endDynamicRendering();

		FloatBuffer triangles = vertexBuffer.floatBuffer();

		// Render main/top border
		int width = window.getWidth();
		int height = window.getHeight();
		var pMouseX = stack.callocFloat(1);
		var pMouseY = stack.callocFloat(1);
		SDL_GetMouseState(pMouseX, pMouseY);
		int borderColor = srgbToLinear(rgb(100, 30, 150));
		putPoint(triangles, 0, 0, borderColor);
		putPoint(triangles, 0, 50, borderColor);
		putPoint(triangles, width, 50, borderColor);
		putPoint(triangles, width, 50, borderColor);
		putPoint(triangles, width, 0, borderColor);
		putPoint(triangles, 0, 0, borderColor);

		// Render left border
		putPoint(triangles, 0, 50, borderColor);
		putPoint(triangles, 0, height, borderColor);
		putPoint(triangles, BORDER_WIDTH, height, borderColor);
		putPoint(triangles, BORDER_WIDTH, height, borderColor);
		putPoint(triangles, BORDER_WIDTH, 50, borderColor);
		putPoint(triangles, 0, 50, borderColor);

		// Render bottom border
		putPoint(triangles, BORDER_WIDTH, height - BORDER_WIDTH, borderColor);
		putPoint(triangles, BORDER_WIDTH, height, borderColor);
		putPoint(triangles, width, height, borderColor);
		putPoint(triangles, width, height, borderColor);
		putPoint(triangles, width, height - BORDER_WIDTH, borderColor);
		putPoint(triangles, BORDER_WIDTH, height - BORDER_WIDTH, borderColor);

		// Render right border
		putPoint(triangles, width - BORDER_WIDTH, 50, borderColor);
		putPoint(triangles, width - BORDER_WIDTH, height, borderColor);
		putPoint(triangles, width, height, borderColor);
		putPoint(triangles, width, height, borderColor);
		putPoint(triangles, width, 50, borderColor);
		putPoint(triangles, width - BORDER_WIDTH, 50, borderColor);

		// Red close triangle
		int closeColor = srgbToLinear(rgb(isMouseOver(-1) ? 255 : 200, 0, 0));
		putPoint(triangles, width + BUTTON_OFFSET - 50, BUTTON_OFFSET, closeColor);
		putPoint(triangles, width - 25, 50 - BUTTON_OFFSET, closeColor);
		putPoint(triangles, width - BUTTON_OFFSET, BUTTON_OFFSET, closeColor);

		// Green maximize triangle
		int maxColor = srgbToLinear(rgb(0, isMouseOver(-2) ? 255 : 200, 0));
		putPoint(triangles, width + BUTTON_OFFSET - 100, BUTTON_OFFSET, maxColor);
		putPoint(triangles, width - 75, 50 - BUTTON_OFFSET, maxColor);
		putPoint(triangles, width - BUTTON_OFFSET - 50, BUTTON_OFFSET, maxColor);

		// Yellow minimize triangle
		int minColor = srgbToLinear(isMouseOver(-3) ? rgb(255, 255, 0) : rgb(200, 200, 0));
		putPoint(triangles, width + BUTTON_OFFSET - 150, BUTTON_OFFSET, minColor);
		putPoint(triangles, width - 125, 50 - BUTTON_OFFSET, minColor);
		putPoint(triangles, width - BUTTON_OFFSET - 100, BUTTON_OFFSET, minColor);

		// Finally, the main triangle
		putPoint(triangles, BORDER_WIDTH, height - BORDER_WIDTH, rgb(255, 0, 0));
		putPoint(triangles, width - BORDER_WIDTH, height - BORDER_WIDTH, rgb(0, 0, 255));
		putPoint(triangles, width / 2, 50, rgb(0, 255, 0));
	}

	@Override
	protected void cleanUp(BoilerInstance boiler) {
		super.cleanUp(boiler);
		memory.destroy(boiler);
		vkDestroyPipeline(boiler.vkDevice(), graphicsPipeline, null);
		vkDestroyPipelineLayout(boiler.vkDevice(), pipelineLayout, null);
	}

	public static void main(String[] args) {
		var boiler = new BoilerBuilder(
				VK_API_VERSION_1_2, "DecoratedTriangle", VK_MAKE_VERSION(0, 1, 0)
		)
				.validation().forbidValidationErrors()
				.addWindow(new WindowBuilder(
						1000, 800, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT
				).sdlFlags(SDL_WINDOW_BORDERLESS | SDL_WINDOW_RESIZABLE | SDL_WINDOW_VULKAN))
				.enableDynamicRendering()
				.useSDL().build();



		var eventLoop = new WindowEventLoop();
		eventLoop.addWindow(new DecoratedTriangle(boiler.window()));
		eventLoop.runMain();

		boiler.destroyInitialObjects();
	}
}