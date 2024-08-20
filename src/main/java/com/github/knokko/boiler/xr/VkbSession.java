package com.github.knokko.boiler.xr;

import org.lwjgl.openxr.*;
import org.lwjgl.system.MemoryStack;

import java.util.function.BiConsumer;

import static com.github.knokko.boiler.xr.OpenXrFailureException.assertXrSuccess;
import static org.lwjgl.openxr.XR10.*;
import static org.lwjgl.system.MemoryStack.stackPush;

public class VkbSession {

	public final XrBoiler xr;
	public final XrSession session;

	public VkbSession(XrBoiler xr, XrSession session) {
		this.xr = xr;
		this.session = session;
	}

	public long chooseSwapchainFormat(MemoryStack stack, long... desiredFormats) {
		var pNumFormats = stack.callocInt(1);
		assertXrSuccess(xrEnumerateSwapchainFormats(
				session, pNumFormats, null
		), "EnumerateSwapchainFormats", "count");
		int numFormats = pNumFormats.get(0);

		var pFormats = stack.callocLong(numFormats);
		assertXrSuccess(xrEnumerateSwapchainFormats(
				session, pNumFormats, pFormats
		), "EnumerateSwapchainFormats", "formats");

		for (long format : desiredFormats) {
			for (int index = 0; index < numFormats; index++) {
				if (pFormats.get(index) == format) return format;
			}
		}

		return pFormats.get(0);
	}

	public XrSpace createReferenceSpace(MemoryStack stack, int type) {
		var ciSpace = XrReferenceSpaceCreateInfo.calloc(stack);
		ciSpace.type$Default();
		ciSpace.referenceSpaceType(type);
		xr.setIdentity(ciSpace.poseInReferenceSpace());

		var pSpace = stack.callocPointer(1);
		assertXrSuccess(xrCreateReferenceSpace(
				session, ciSpace, pSpace
		), "CreateReferenceSpace", "Type " + type);
		return new XrSpace(pSpace.get(0), session);
	}

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
				session, liView, viewState, pNumViews, pViews
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

	public void begin(int viewConfiguration, String context) {
		try (var stack = stackPush()) {
			var biSession = XrSessionBeginInfo.calloc(stack);
			biSession.type$Default();
			biSession.primaryViewConfigurationType(viewConfiguration);

			assertXrSuccess(xrBeginSession(session, biSession), "BeginSession", context);
		}
	}

	public XrSpace createActionSpace(MemoryStack stack, XrAction action, long subactionPath, String context) {
		var ciSpace = XrActionSpaceCreateInfo.calloc(stack);
		ciSpace.type$Default();
		ciSpace.action(action);
		ciSpace.subactionPath(subactionPath);
		xr.setIdentity(ciSpace.poseInActionSpace());

		var pSpace = stack.callocPointer(1);
		assertXrSuccess(xrCreateActionSpace(
				session, ciSpace, pSpace
		), "CreateActionSpace", context);
		return new XrSpace(pSpace.get(0), session);
	}

	public void attach(MemoryStack stack, XrActionSet actionSet) {
		var aiSession = XrSessionActionSetsAttachInfo.calloc(stack);
		aiSession.type$Default();
		aiSession.actionSets(stack.pointers(actionSet));

		assertXrSuccess(xrAttachSessionActionSets(
				session, aiSession
		), "AttachSessionActionSets", null);
	}
}
