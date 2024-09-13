package com.github.knokko.boiler.xr;

import org.lwjgl.openxr.XrAction;
import org.lwjgl.openxr.XrActionSuggestedBinding;
import org.lwjgl.openxr.XrInteractionProfileSuggestedBinding;

import java.util.ArrayList;
import java.util.List;

import static com.github.knokko.boiler.exceptions.OpenXrFailureException.assertXrSuccess;
import static org.lwjgl.openxr.XR10.xrSuggestInteractionProfileBindings;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * A class to help with suggesting interaction profile bindings. By using this class, it should become a lot less
 * verbose. You can use the public constructor of this class to create an instance.
 */
public class SuggestedBindingsBuilder {

	private final XrBoiler xr;
	private final String interactionProfile;
	private final List<Entry> entries = new ArrayList<>();

	/**
	 * @param xr The <i>XrBoiler</i> (you can get it from <i>boilerInstance.xr()</i>)
	 * @param interactionProfile The interaction profile for which you are going to suggest bindings. For instance
	 *                           "/interaction_profiles/khr/simple_controller"
	 */
	public SuggestedBindingsBuilder(XrBoiler xr, String interactionProfile) {
		this.xr = xr;
		this.interactionProfile = interactionProfile;
	}

	/**
	 * Adds an <i>XrActionSuggestedBinding</i> to bind the given action to the given path. <i>xrStringToPath</i> will be
	 * used to convert the path string to an <i>XrPath</i>
	 */
	public void add(XrAction action, String path) {
		entries.add(new Entry(action, path));
	}

	/**
	 * Calls <i>xrSuggestInteractionProfileBindings</i> with all bindings that have been added so far using the
	 * <i>add</i> method of this class.
	 */
	@SuppressWarnings("resource")
	public void finish() {
		try (var stack = stackPush()) {
			var suggestedBindings = XrActionSuggestedBinding.calloc(entries.size(), stack);
			for (int index = 0; index < entries.size(); index++) {
				suggestedBindings.get(index).action(entries.get(index).action());
				suggestedBindings.get(index).binding(xr.actions.getPath(stack, entries.get(index).path()));
			}

			var suggestedInteractionBindings = XrInteractionProfileSuggestedBinding.calloc(stack);
			suggestedInteractionBindings.type$Default();
			suggestedInteractionBindings.interactionProfile(xr.actions.getPath(stack, interactionProfile));
			suggestedInteractionBindings.suggestedBindings(suggestedBindings);

			assertXrSuccess(xrSuggestInteractionProfileBindings(
					xr.xrInstance, suggestedInteractionBindings
			), "SuggestInteractionProfileBindings", interactionProfile);
		}
	}

	private record Entry(XrAction action, String path) {}
}
