package com.github.knokko.boiler.xr;

import com.github.knokko.boiler.BoilerInstance;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.openxr.*;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;
import java.util.function.Consumer;

import static com.github.knokko.boiler.exceptions.OpenXrFailureException.assertXrSuccess;
import static org.joml.Math.tan;
import static org.lwjgl.openxr.XR10.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * This class provides some convenience methods when working with Vulkan and OpenXR. You can obtain the instance from
 * <i>boilerInstance.xr()</i>
 */
public class XrBoiler {

	public final XrInstance xrInstance;
	public final long xrSystem;

	public final BoilerActions actions = new BoilerActions(this);

	public BoilerInstance boilerInstance;

	/**
	 * Note: this constructor is meant for internal use. You should normally get the instance of this class via
	 * <i>boilerInstance.xr()</i>
	 */
	public XrBoiler(XrInstance xrInstance, long xrSystemId) {
		this.xrInstance = xrInstance;
		this.xrSystem = xrSystemId;
	}

	/**
	 * Calls <i>xrCreateSession</i>, and returns the wrapped result. The <i>XrGraphicsBindingVulkan2KHR</i> will use
	 * the graphics queue family of the <i>BoilerInstance</i>. From this queue family, it will use the graphics queue
	 * with the given <i>queueIndex</i>.
	 */
	public VkbSession createSession(int queueIndex, String context) {
		try (var stack = stackPush()) {
			var vulkanBinding = XrGraphicsBindingVulkan2KHR.calloc(stack);
			vulkanBinding.type$Default();
			vulkanBinding.instance(boilerInstance.vkInstance());
			vulkanBinding.physicalDevice(boilerInstance.vkPhysicalDevice());
			vulkanBinding.device(boilerInstance.vkDevice());
			vulkanBinding.queueFamilyIndex(boilerInstance.queueFamilies().graphics().index());
			vulkanBinding.queueIndex(queueIndex);

			var ciSession = XrSessionCreateInfo.calloc(stack);
			ciSession.type$Default();
			ciSession.next(vulkanBinding.address());
			ciSession.createFlags(0);
			ciSession.systemId(xrSystem);

			var pSession = stack.callocPointer(1);
			assertXrSuccess(xrCreateSession(
					xrInstance, ciSession, pSession
			), "CreateSession", context);
			return new VkbSession(this, new XrSession(pSession.get(0), xrInstance));
		}
	}

	/**
	 * Calls <i>xrEnumerateViewConfigurations</i> and <i>xrEnumerateViewConfigurationViews</i>. The resulting
	 * <i>XrViewConfigurationViewBuffer</i> will be returned.
	 * @param stack The memory stack onto which the result (and some other structs) will be allocated
	 * @param viewConfiguration The required <i>XrViewConfigurationType</i> that will be passed to
	 *                          <i>xrEnumerateViewConfigurationViews</i>. If it's not listed by
	 *                          <i>xrEnumerateViewConfigurations</i>, an <i>UnsupportedOperationException</i> will be
	 *                          thrown.
	 * @param requireIdenticalViews Whether all views returned by <i>xrEnumerateViewConfigurationViews</i> must be
	 *                              identical. If this is <b>true</b> and the views are not identical, an
	 *                              <i>UnsupportedOperationException</i> will be thrown.
	 * @return The <i>XrViewConfigurationView</i> buffer that was populated by <i>xrEnumerateViewConfigurationViews</i>
	 */
	public XrViewConfigurationView.Buffer getViewConfigurationViews(
			MemoryStack stack, int viewConfiguration, boolean requireIdenticalViews
	) {
		IntBuffer pNumConfigs = stack.callocInt(1);
		assertXrSuccess(xrEnumerateViewConfigurations(
				xrInstance, xrSystem, pNumConfigs, null
		), "EnumerateViewConfigurations", "count");
		int numConfigs = pNumConfigs.get(0);

		IntBuffer pConfigs = stack.callocInt(numConfigs);
		assertXrSuccess(xrEnumerateViewConfigurations(
				xrInstance, xrSystem, pNumConfigs, pConfigs
		), "EnumerateViewConfigurations", "configs");

		boolean hasConfiguration = false;
		for (int index = 0; index < numConfigs; index++) {
			if (pConfigs.get(index) == viewConfiguration) hasConfiguration = true;
		}
		if (!hasConfiguration) throw new UnsupportedOperationException("View configuration is not supported");

		IntBuffer pNumViews = stack.callocInt(1);
		assertXrSuccess(xrEnumerateViewConfigurationViews(
				xrInstance, xrSystem, viewConfiguration, pNumViews, null
		), "EnumerateViewConfigurationViews", "count");
		int numViews = pNumViews.get(0);

		var pViews = XrViewConfigurationView.calloc(numViews, stack);
		for (int index = 0; index < numViews; index++) {
			//noinspection resource
			pViews.get(index).type$Default();
		}
		assertXrSuccess(xrEnumerateViewConfigurationViews(
				xrInstance, xrSystem, viewConfiguration, pNumViews, pViews
		), "EnumerateViewConfigurationViews", "views");

		if (requireIdenticalViews && numViews > 1) {
			var view1 = pViews.get(0);
			for (int index = 1; index < numViews; index++) {
				var view = pViews.get(1);
				if (
						view.recommendedImageRectWidth() != view1.recommendedImageRectWidth() ||
								view.recommendedImageRectHeight() != view1.recommendedImageRectHeight() ||
								view.recommendedSwapchainSampleCount() != view1.recommendedSwapchainSampleCount() ||
								view.maxImageRectWidth() != view1.maxImageRectWidth() ||
								view.maxImageRectHeight() != view1.maxImageRectHeight() ||
								view.maxSwapchainSampleCount() != view1.maxSwapchainSampleCount()
				) {
					throw new UnsupportedOperationException("View configuration views are not identical");
				}
			}
		}
		return pViews;
	}

	/**
	 * Calls <i>xrEnumerateSwapchainImages</i> to query and return the array of swapchain images
	 */
	public long[] getSwapchainImages(XrSwapchain swapchain) {
		try (var stack = stackPush()) {
			var pNumImages = stack.callocInt(1);
			assertXrSuccess(xrEnumerateSwapchainImages(
					swapchain, pNumImages, null
			), "EnumerateSwapchainImages", "count");
			int numImages = pNumImages.get(0);

			var pImages = XrSwapchainImageVulkan2KHR.calloc(numImages, stack);
			for (int index = 0; index < numImages; index++) {
				//noinspection resource
				pImages.get(index).type$Default();
			}

			assertXrSuccess(xrEnumerateSwapchainImages(
					swapchain, pNumImages, XrSwapchainImageBaseHeader.create(pImages.address(), numImages)
			), "EnumerateSwapchainImages", "images");

			long[] result = new long[numImages];
			for (int index = 0; index < numImages; index++) {
				result[index] = pImages.get(index).image();
			}
			return result;
		}
	}

	/**
	 * Sets the given pose to the identity position (0, 0, 0) and orientation (0, 0, 0, 1)
	 */
	public void setIdentity(XrPosef pose) {
		pose.orientation().set(0f, 0f, 0f, 1f);
		pose.position$().set(0f, 0f, 0f);
	}

	/**
	 * Creates a projection matrix with the given fov, nearZ/nearPlane, and farZ/farPlane
	 */
	public Matrix4f createProjectionMatrix(XrFovf fov, float nearZ, float farZ) {
		// Most of this math is ripped from
		// https://github.com/LWJGL/lwjgl3/blob/92777ed4102ae8185df1042687306a587e7cd88b/modules/samples/src/test/java/org/lwjgl/demo/openxr/XRHelper.java#L98
		Matrix4f projectionMatrix = new Matrix4f().scale(1f, -1f, 1f);
		float distToLeftPlane = tan(fov.angleLeft());
		float distToRightPlane = tan(fov.angleRight());
		float distToBottomPlane = tan(fov.angleDown());
		float distToTopPlane = tan(fov.angleUp());
		return projectionMatrix.frustum(
				distToLeftPlane * nearZ, distToRightPlane * nearZ,
				distToBottomPlane * nearZ, distToTopPlane * nearZ,
				nearZ, farZ, true
		);
	}

	/**
	 * Repeatedly calls <i>xrPollEvent</i> until it returns <i>XR_EVENT_UNAVAILABLE</i>. For each polled event, it
	 * calls the <i>processEvent</i> callback
	 */
	public void pollEvents(MemoryStack stack, String context, Consumer<XrEventDataBuffer> processEvent) {
		var eventData = XrEventDataBuffer.malloc(stack);

		while (true) {
			eventData.type$Default();
			eventData.next(0L);

			int pollResult = xrPollEvent(xrInstance, eventData);
			if (pollResult == XR_EVENT_UNAVAILABLE) return;
			assertXrSuccess(pollResult, "PollEvent", context);
			processEvent.accept(eventData);
		}
	}

	/**
	 * Calls <i>xrLocateSpace</i> with the given <i>space</i>, <i>baseSpace</i>, and <i>time</i>. The result will be
	 * converted to a JOML vector and quaternion.
	 * @param stack The memory stack onto which this method will allocate some structs
	 * @param space The <i>space</i> parameter passed to <i>xrLocateSpace</i>
	 * @param baseSpace The <i>baseSpace</i> parameter passed to <i>xrLocateSpace</i>
	 * @param time The <i>time</i> parameter passed to <i>xrLocateSpace</i>
	 * @param context When <i>xrLocateSpace</i> doesn't return <i>XR_SUCCESS</i>, and exception will be thrown, which
	 *                will contain <i>context</i> in its message
	 * @return A <i>LocatedSpace</i> instance with the located position and orientation
	 */
	public LocatedSpace locateSpace(MemoryStack stack, XrSpace space, XrSpace baseSpace, long time, String context) {
		var rawLocation = XrSpaceLocation.calloc(stack);
		rawLocation.type$Default();

		assertXrSuccess(xrLocateSpace(
				space, baseSpace, time, rawLocation
		), "LocateSpace", context);

		Vector3f position = null;
		if ((rawLocation.locationFlags() & XR_SPACE_LOCATION_POSITION_VALID_BIT) != 0) {
			var pos = rawLocation.pose().position$();
			position = new Vector3f(pos.x(), pos.y(), pos.z());
		}

		Quaternionf orientation = null;
		if ((rawLocation.locationFlags() & XR_SPACE_LOCATION_ORIENTATION_VALID_BIT) != 0) {
			var rot = rawLocation.pose().orientation();
			orientation = new Quaternionf(rot.x(), rot.y(), rot.z(), rot.w());
		}

		return new LocatedSpace(position, orientation);
	}

	/**
	 * Destroys all OpenXR objects that were created by the <i>BoilerBuilder</i> (currently just the <i>XrInstance</i>).
	 * Note that this method will be called during <i>BoilerInstance.destroy</i>, so applications should <b>not</b> call
	 * this method.
	 */
	public void destroyInitialObjects() {
		assertXrSuccess(xrDestroyInstance(xrInstance), "DestroyInstance", "XrBoiler");
	}
}
