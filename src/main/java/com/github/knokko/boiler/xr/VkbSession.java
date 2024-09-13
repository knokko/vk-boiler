package com.github.knokko.boiler.xr;

import org.lwjgl.openxr.*;
import org.lwjgl.system.MemoryStack;

import java.util.function.BiConsumer;

import static com.github.knokko.boiler.exceptions.OpenXrFailureException.assertXrSuccess;
import static org.lwjgl.openxr.XR10.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * A wrapper around an <i>XrSession</i> with some potentially convenient instance methods
 */
public class VkbSession {

	public final XrBoiler xr;
	public final XrSession xrSession;

	/**
	 * Note: you could use this constructor, but I would recommend using <i>boiler.xr().createSession</i> instead.
	 */
	public VkbSession(XrBoiler xr, XrSession xrSession) {
		this.xr = xr;
		this.xrSession = xrSession;
	}

	/**
	 * Chooses an image format that can be used to create the <i>XrSwapchain</i>. <i>xrEnumerateSwapchainFormats</i>
	 * will be used to determine which swapchain formats are supported by the OpenXR runtime.
	 * @param desiredFormats An array of <i>VkFormat</i>s, where the formats that appear earlier in the array are
	 *                       preferred over the formats that appear later.
	 * @return The first <i>VkFormat</i> in <i>desiredFormats</i> that is supported by the OpenXR runtime. If none of
	 * the <i>desiredFormats</i> is supported, the first format that is given by <i>xrEnumerateSwapchainFormats</i> will
	 * be returned instead.
	 */
	public int chooseSwapchainFormat(int... desiredFormats) {
		try (var stack = stackPush()) {
			var pNumFormats = stack.callocInt(1);
			assertXrSuccess(xrEnumerateSwapchainFormats(
					xrSession, pNumFormats, null
			), "EnumerateSwapchainFormats", "count");
			int numFormats = pNumFormats.get(0);

			var pFormats = stack.callocLong(numFormats);
			assertXrSuccess(xrEnumerateSwapchainFormats(
					xrSession, pNumFormats, pFormats
			), "EnumerateSwapchainFormats", "formats");

			for (int format : desiredFormats) {
				for (int index = 0; index < numFormats; index++) {
					if (pFormats.get(index) == format) return format;
				}
			}

			return (int) pFormats.get(0);
		}
	}

	/**
	 * Calls <i>xrCreateSwapchain</i> to create an <i>XrSwapchain</i>, using the given parameters. All other fields of
	 * the <i>XrSwapchainCreateInfo</i> will be populated using (sensible) default values.
	 * @param arraySize The number of array layers, typically 2 when you use multiview to render to both eyes
	 * @param width The width, in pixels
	 * @param height The height, in pixels
	 * @param format The <i>VkFormat</i>, typically selected using <i>chooseSwapchainFormat</i>
	 * @param usageFlags The <i>VkImageUsageFlagBits</i>, typically <i>VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT</i>
	 * @return The created <i>XrSwapchain</i>
	 */
	public XrSwapchain createSwapchain(int arraySize, int width, int height, int format, int usageFlags) {
		try (var stack = stackPush()) {
			var ciSwapchain = XrSwapchainCreateInfo.calloc(stack);
			ciSwapchain.type$Default();
			ciSwapchain.createFlags(0);
			ciSwapchain.usageFlags(usageFlags);
			ciSwapchain.format(format);
			ciSwapchain.sampleCount(1);
			ciSwapchain.width(width);
			ciSwapchain.height(height);
			ciSwapchain.faceCount(1);
			ciSwapchain.arraySize(arraySize);
			ciSwapchain.mipCount(1);

			var pSwapchain = stack.callocPointer(1);
			assertXrSuccess(xrCreateSwapchain(
					xrSession, ciSwapchain, pSwapchain
			), "CreateSwapchain", "VkbSession.createSwapchain");
			return new XrSwapchain(pSwapchain.get(0), xrSession);
		}
	}

	/**
	 * Calls <i>xrCreateReferenceSpace</i> to create (and return) a reference space with identity pose using the given
	 * <i>XrReferenceSpaceType</i>
	 * @param stack The memory stack onto which the <i>XrReferenceSpaceCreateInfo</i> will be allocated
	 * @param type The <i>XrReferenceSpaceType</i>, for instance <i>XR_REFERENCE_SPACE_TYPE_STAGE</i>
	 */
	public XrSpace createReferenceSpace(MemoryStack stack, int type) {
		var ciSpace = XrReferenceSpaceCreateInfo.calloc(stack);
		ciSpace.type$Default();
		ciSpace.referenceSpaceType(type);
		xr.setIdentity(ciSpace.poseInReferenceSpace());

		var pSpace = stack.callocPointer(1);
		assertXrSuccess(xrCreateReferenceSpace(
				xrSession, ciSpace, pSpace
		), "CreateReferenceSpace", "Type " + type);
		return new XrSpace(pSpace.get(0), xrSession);
	}

	/**
	 * Calls <i>xrCreateActionSpace</i> to create and return an action space with identity pose for the given
	 * (sub)action.
	 * @param stack The memory stack onto which the <i>XrActionSpaceCreateInfo</i> will be allocated
	 * @param action The <i>XrAction</i>
	 * @param subactionPath The subaction path, may be <i>XR_NULL_PATH</i>
	 * @param context When <i>xrCreateActionSpace</i> does not return <i>XR_SUCCESS</i>, an exception will be thrown,
	 *                which will contain <i>context</i> in its message
	 * @return The created action space
	 */
	public XrSpace createActionSpace(MemoryStack stack, XrAction action, long subactionPath, String context) {
		var ciSpace = XrActionSpaceCreateInfo.calloc(stack);
		ciSpace.type$Default();
		ciSpace.action(action);
		ciSpace.subactionPath(subactionPath);
		xr.setIdentity(ciSpace.poseInActionSpace());

		var pSpace = stack.callocPointer(1);
		assertXrSuccess(xrCreateActionSpace(
				xrSession, ciSpace, pSpace
		), "CreateActionSpace", context);
		return new XrSpace(pSpace.get(0), xrSession);
	}

	/**
	 * Calls <i>xrAttachSessionActionSets</i> to attach a single <i>XrActionSet</i> to this <i>XrSession</i>
	 * @param stack The memory stack onto which the <i>XrSessionActionSetsAttachInfo</i> will be allocated
	 * @param actionSet The <i>XrActionSet</i> to be attached
	 */
	public void attach(MemoryStack stack, XrActionSet actionSet) {
		var aiSession = XrSessionActionSetsAttachInfo.calloc(stack);
		aiSession.type$Default();
		aiSession.actionSets(stack.pointers(actionSet));

		assertXrSuccess(xrAttachSessionActionSets(
				xrSession, aiSession
		), "AttachSessionActionSets", null);
	}

	/**
	 * Calls <i>xrBeginSession</i> to begin a session, using the given <i>viewConfiguration</i> as
	 * <i>primaryViewConfigurationType</i>
	 */
	public void begin(int viewConfiguration, String context) {
		try (var stack = stackPush()) {
			var biSession = XrSessionBeginInfo.calloc(stack);
			biSession.type$Default();
			biSession.primaryViewConfigurationType(viewConfiguration);

			assertXrSuccess(xrBeginSession(xrSession, biSession), "BeginSession", context);
		}
	}

	/**
	 * Calls <i>xrLocateViews</i> with the given <i>renderSpace</i>, <i>viewConfigType</i>, and <i>displayTime</i>.
	 * If both the resulting position and orientation are valid, this method will return a
	 * <i>XrCompositionLayerProjectionView</i> buffer that can be passed to <i>xrEndFrame</i>. If either the position
	 * or orientation is invalid, returns <b>null</b> instead.
	 * <p>
	 *   This method is used in the <i>SessionLoop</i> class, so only applications that do <b>not</b> use
	 *   <i>SessionLoop</i> need to call this method directly.
	 * </p>
	 *
	 * @param stack The memory stack onto which the result (and some other structs) will be allocated
	 * @param renderSpace The <i>renderSpace</i> passed to the <i>XrViewLocateInfo</i>
	 * @param numViews The number of views, probably 2 (one per eye)
	 * @param viewConfigType The <i>viewConfigurationType</i> passed to the <i>XrViewLocateInfo</i>
	 * @param displayTime The <i>displayTime</i> passed to the <i>XrViewLocateInfo</i>
	 * @param populateSubImage This callback will be called once for each element of the returned
	 *                         <i>XrCompositionLayerProjectionView</i>
	 * @return The <i>XrCompositionLayerProjectionView</i> buffer that can be used in <i>xrEndFrame</i>, or
	 * <b>null</b> if either the position or orientation is invalid
	 */
	public XrCompositionLayerProjectionView.Buffer createProjectionViews(
			MemoryStack stack, XrSpace renderSpace,
			int numViews, int viewConfigType, long displayTime,
			BiConsumer<XrSwapchainSubImage, Integer> populateSubImage
	) {
		var pNumViews = stack.ints(numViews);
		var pViews = XrView.calloc(numViews, stack);
		for (int index = 0; index < numViews; index++) {
			//noinspection resource
			pViews.get(index).type$Default();
		}

		var viewState = XrViewState.calloc(stack);
		viewState.type$Default();

		var liView = XrViewLocateInfo.calloc(stack);
		liView.type$Default();
		liView.viewConfigurationType(viewConfigType);
		liView.displayTime(displayTime);
		liView.space(renderSpace);

		assertXrSuccess(xrLocateViews(
				xrSession, liView, viewState, pNumViews, pViews
		), "LocateViews", "projection count");

		if ((viewState.viewStateFlags() & XR_VIEW_STATE_POSITION_VALID_BIT) == 0) return null;
		if ((viewState.viewStateFlags() & XR_VIEW_STATE_ORIENTATION_VALID_BIT) == 0) return null;

		var projectionViews = XrCompositionLayerProjectionView.calloc(numViews, stack);
		for (int index = 0; index < numViews; index++) {
			var view = pViews.get(index);

			var projectionView = projectionViews.get(index);
			projectionView.type$Default();
			projectionView.pose(view.pose());
			projectionView.fov(view.fov());
			populateSubImage.accept(projectionView.subImage(), index);
		}

		return projectionViews;
	}

	/**
	 * Allocates an <i>XrActionStateGetInfo</i> on the given stack, and populates it with the given action, and returns
	 * it.
	 */
	public XrActionStateGetInfo prepareActionState(MemoryStack stack, XrAction action) {
		return prepareSubactionState(stack, action, XR_NULL_PATH);
	}

	/**
	 * Allocates an <i>XrActionStateGetInfo</i> on the given stack, and populates it with the given action and
	 * subaction path, and returns it.
	 */
	public XrActionStateGetInfo prepareSubactionState(MemoryStack stack, XrAction action, long subaction) {
		var giActionState = XrActionStateGetInfo.calloc(stack);
		giActionState.type$Default();
		giActionState.action(action);
		giActionState.subactionPath(subaction);
		return giActionState;
	}

	/**
	 * Allocates an <i>XrActionStateBoolean</i> onto the given stack, calls <i>xrGetActionStateBoolean</i>, and
	 * returns it.
	 */
	public XrActionStateBoolean getBooleanAction(MemoryStack stack, XrActionStateGetInfo giAction, String context) {
		var pState = XrActionStateBoolean.calloc(stack);
		pState.type$Default();

		assertXrSuccess(xrGetActionStateBoolean(
				xrSession, giAction, pState
		), "GetActionStateBoolean", context);
		return pState;
	}

	/**
	 * Allocates an <i>XrActionStateFloat</i> onto the given stack, calls <i>xrGetActionStateFloat</i>, and
	 * returns it.
	 */
	public XrActionStateFloat getFloatAction(MemoryStack stack, XrActionStateGetInfo giAction, String context) {
		var pState = XrActionStateFloat.calloc(stack);
		pState.type$Default();

		assertXrSuccess(xrGetActionStateFloat(
				xrSession, giAction, pState
		), "GetActionStateFloat", context);
		return pState;
	}

	/**
	 * Allocates an <i>XrActionStateVector2f</i> onto the given stack, calls <i>xrGetActionStateVector2f</i>, and
	 * returns it.
	 */
	public XrActionStateVector2f getVectorAction(MemoryStack stack, XrActionStateGetInfo giAction, String context) {
		var pState = XrActionStateVector2f.calloc(stack);
		pState.type$Default();

		assertXrSuccess(xrGetActionStateVector2f(
				xrSession, giAction, pState
		), "GetActionStateVector2f", context);
		return pState;
	}

	/**
	 * Allocates an <i>XrActionStatePose</i> onto the given stack, calls <i>xrGetActionStatePose</i>, and
	 * returns it.
	 */
	public XrActionStatePose getPoseAction(MemoryStack stack, XrActionStateGetInfo giAction, String context) {
		var pState = XrActionStatePose.calloc(stack);
		pState.type$Default();

		assertXrSuccess(xrGetActionStatePose(
				xrSession, giAction, pState
		), "GetActionStatePose", context);
		return pState;
	}
}
