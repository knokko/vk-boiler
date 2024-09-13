package com.github.knokko.boiler.xr;

import org.lwjgl.openxr.*;
import org.lwjgl.system.MemoryStack;

import static com.github.knokko.boiler.exceptions.OpenXrFailureException.assertXrSuccess;
import static org.lwjgl.openxr.XR10.*;
import static org.lwjgl.openxr.XR10.xrCreateAction;

/**
 * This class contains convenience methods to work with OpenXR actions, action sets, and paths. You can access the
 * instance of this class from <i>boilerInstance.xr().actions</i>
 */
public class BoilerActions {

	private final XrBoiler xr;

	BoilerActions(XrBoiler xr) {
		this.xr = xr;
	}

	/**
	 * Calls <i>xrCreateActionSet</i> with the given parameters to create an <i>XrActionSet</i>. All parameters
	 * (except <i>stack</i>) will simply be propagated to the <i>XrActionSetCreateInfo</i>.
	 * @param stack The memory stack onto which the <i>XrActionSetCreateInfo</i> will be allocated
	 * @return The created <i>XrActionSet</i>
	 */
	public XrActionSet createSet(MemoryStack stack, int priority, String name, String localizedName) {
		var ciActionSet = XrActionSetCreateInfo.calloc(stack);
		ciActionSet.type$Default();
		ciActionSet.actionSetName(stack.UTF8(name));
		ciActionSet.localizedActionSetName(stack.UTF8(localizedName));
		ciActionSet.priority(priority);

		var pActionSet = stack.callocPointer(1);
		assertXrSuccess(xrCreateActionSet(
				xr.xrInstance, ciActionSet, pActionSet
		), "CreateActionSet", name);
		return new XrActionSet(pActionSet.get(0), xr.xrInstance);
	}

	/**
	 * Calls <i>xrStringToPath</i> to convert the given path string to a raw <i>XrPath</i> (which is a <i>long</i>).
	 * @param stack The memory stack onto which the result will be allocated
	 * @param path The path string
	 * @return The <i>XrPath</i> handle as <i>long</i>
	 */
	public long getPath(MemoryStack stack, String path) {
		var pPath = stack.callocLong(1);
		assertXrSuccess(xrStringToPath(
				xr.xrInstance, stack.UTF8(path), pPath
		), "StringToPath", path);
		return pPath.get(0);
	}

	/**
	 * Calls <i>xrCreateAction</i> to create (and return) an <i>XrAction</i> without subactions. All parameters
	 * (except <i>stack</i>) will simply be propagated to the <i>XrActionCreateInfo</i>.
	 * @param stack The memory stack onto which the <i>XrActionCreateInfo</i> will be allocated
	 */
	public XrAction create(MemoryStack stack, XrActionSet actionSet, String name, String localizedName, int actionType) {
		return createWithSubactions(stack, actionSet, name, localizedName, actionType);
	}

	/**
	 * Calls <i>xrCreateAction</i> to create (and return) an <i>XrAction</i> with subactions. All parameters
	 * (except <i>stack</i>) will simply be propagated to the <i>XrActionCreateInfo</i>.
	 * @param stack The memory stack onto which the <i>XrActionCreateInfo</i> will be allocated
	 */
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
		if (subactionPaths.length > 0) ciAction.subactionPaths(stack.longs(subactionPaths));
		ciAction.localizedActionName(stack.UTF8(localizedName));

		var pAction = stack.callocPointer(1);
		assertXrSuccess(xrCreateAction(
				actionSet, ciAction, pAction
		), "CreateAction", name);
		return new XrAction(pAction.get(0), actionSet);
	}
}
