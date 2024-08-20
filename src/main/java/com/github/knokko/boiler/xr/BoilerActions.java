package com.github.knokko.boiler.xr;

import org.lwjgl.openxr.*;
import org.lwjgl.system.MemoryStack;

import static com.github.knokko.boiler.xr.OpenXrFailureException.assertXrSuccess;
import static org.lwjgl.openxr.XR10.*;
import static org.lwjgl.openxr.XR10.xrCreateAction;

public class BoilerActions {

	private final XrBoiler xr;

	BoilerActions(XrBoiler xr) {
		this.xr = xr;
	}

	public XrActionSet createSet(MemoryStack stack, int priority, String name, String localizedName) {
		var ciActionSet = XrActionSetCreateInfo.calloc(stack);
		ciActionSet.type$Default();
		ciActionSet.actionSetName(stack.UTF8(name));
		ciActionSet.localizedActionSetName(stack.UTF8(localizedName));
		ciActionSet.priority(priority);

		var pActionSet = stack.callocPointer(1);
		assertXrSuccess(xrCreateActionSet(
				xr.instance, ciActionSet, pActionSet
		), "CreateActionSet", name);
		return new XrActionSet(pActionSet.get(0), xr.instance);
	}

	public long getPath(MemoryStack stack, String path) {
		var pPath = stack.callocLong(1);
		assertXrSuccess(xrStringToPath(
				xr.instance, stack.UTF8(path), pPath
		), "StringToPath", path);
		return pPath.get(0);
	}

	public XrAction createWithSubactions(
			MemoryStack stack, XrActionSet actionSet,
			String name, String localizedName,
			int actionType, long... subactionPaths
	) {
		var ciAction = XrActionCreateInfo.calloc(stack);
		ciAction.type$Default();
		ciAction.actionName(stack.UTF8(name));
		ciAction.actionType(actionType);
		ciAction.countSubactionPaths(subactionPaths.length);
		ciAction.subactionPaths(stack.longs(subactionPaths));
		ciAction.localizedActionName(stack.UTF8(localizedName));

		var pAction = stack.callocPointer(1);
		assertXrSuccess(xrCreateAction(
				actionSet, ciAction, pAction
		), "CreateAction", name);
		return new XrAction(pAction.get(0), actionSet);
	}
}
