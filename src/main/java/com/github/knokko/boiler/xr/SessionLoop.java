package com.github.knokko.boiler.xr;

import org.joml.Matrix4f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.openxr.*;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static com.github.knokko.boiler.exceptions.OpenXrFailureException.assertXrSuccess;
import static java.lang.Thread.sleep;
import static org.lwjgl.openxr.XR10.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/**
 * <p>
 *   This class can be used to manage an OpenXR 'session loop' for an application. This class can be used by creating
 *   a subclass, implementing the abstract methods, and calling its <i>run</i> method.
 * </p>
 *
 * <p>
 *   The <i>run</i> method of this class will try to <i>begin</i> the session, and keeps track of the current session
 *   state. It also makes sure that it will <i>end</i> the session when the application or runtime wants to. The
 *   <i>run</i> method is made to deal with the logic that is usually not interesting for the application. It will
 *   however call the abstract methods of this class, which strongly depend on the application.
 * </p>
 */
public abstract class SessionLoop {

	protected final VkbSession session;
	protected final XrBoiler xr;
	protected final XrSpace renderSpace;
	protected final XrSwapchain swapchain;
	protected final int width, height;

	private int state;
	private boolean isRunning;
	private boolean didRequestExit;
	private volatile boolean wantsToStop;

	public SessionLoop(
			VkbSession session, XrSpace renderSpace, XrSwapchain swapchain, int width, int height
	) {
		this.session = session;
		this.xr = session.xr;
		this.renderSpace = renderSpace;
		this.swapchain = swapchain;
		this.width = width;
		this.height = height;
	}

	/**
	 * Requests the runtime to stop the session. The <i>xrRequestExitSession</i> function will be called within 1
	 * iteration, unless it has already been called. Calling this method more than once has no effect.
	 */
	public void requestExit() {
		this.wantsToStop = true;
	}

	/**
	 * Gets the current <i>XrSessionState</i>. Note that many applications won't need this information, but it could
	 * be useful to some.
	 */
	public int getState() {
		return state;
	}

	/**
	 * Tries to <i>begin</i> the session, after which it will manage the session until the application or OpenXR runtime
	 * wants to end it. During the session, it will call the abstract methods of this class.
	 */
	public void run() {
		int numViews = getNumViews();
		state = XR_SESSION_STATE_IDLE;

		Matrix4f[] lastCameraMatrix = new Matrix4f[numViews];
		for (int index = 0; index < numViews; index++) lastCameraMatrix[index] = new Matrix4f();

		while (true) {
			try (var stack = stackPush()) {
				xr.pollEvents(stack, null, eventData -> {
					if (eventData.type() == XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED) {
						this.state = XrEventDataSessionStateChanged.nstate(eventData.address());
						System.out.println("new session state is " + this.state);
					}
					handleEvent(eventData);
				});

				update();

				if ((didRequestExit && !isRunning)
						|| this.state == XR_SESSION_STATE_EXITING || this.state == XR_SESSION_STATE_LOSS_PENDING
				) {
					assertXrSuccess(vkQueueWaitIdle(
							xr.boilerInstance.queueFamilies().graphics().first().vkQueue()
					), "QueueWaitIdle", "End of last frame");
					break;
				}

				if (this.state == XR_SESSION_STATE_STOPPING) {
					assertXrSuccess(xrEndSession(session.xrSession), "EndSession", null);
					isRunning = false;
					continue;
				}

				if (this.state == XR_SESSION_STATE_IDLE) {
					try {
						//noinspection BusyWait
						sleep(getIdleSleepTime());
					} catch (InterruptedException shouldNotHappen) {
						// ignored
					}
					continue;
				}

				if (isRunning && !didRequestExit && wantsToStop) {
					assertXrSuccess(xrRequestExitSession(session.xrSession), "RequestExitSession", null);
					didRequestExit = true;
					continue;
				}

				if (this.state == XR_SESSION_STATE_READY && !isRunning) {
					session.begin(getViewConfigurationType(), "SessionLoop");
					isRunning = true;
					continue;
				}

				if (this.state == XR_SESSION_STATE_SYNCHRONIZED || this.state == XR_SESSION_STATE_VISIBLE ||
						this.state == XR_SESSION_STATE_FOCUSED || this.state == XR_SESSION_STATE_READY
				) {
					waitForRenderResources(stack);

					var frameState = XrFrameState.calloc(stack);
					frameState.type$Default();

					assertXrSuccess(xrWaitFrame(
							session.xrSession, null, frameState
					), "WaitFrame", null);
					assertXrSuccess(xrBeginFrame(
							session.xrSession, null
					), "BeginFrame", null);

					PointerBuffer layers = null;
					if (frameState.shouldRender()) {
						var projectionViews = session.createProjectionViews(
								stack, renderSpace, numViews, getViewConfigurationType(),
								frameState.predictedDisplayTime(), (subImage, index) -> {
									subImage.swapchain(swapchain);
									subImage.imageRect().offset().set(0, 0);
									subImage.imageRect().extent().set(width, height);
									subImage.imageArrayIndex(index);
								}
						);

						var layer = XrCompositionLayerProjection.calloc(stack);
						layer.type$Default();
						layer.layerFlags(getLayerFlags());
						layer.space(renderSpace);
						if (projectionViews != null) {
							layer.views(projectionViews);
							layers = stack.pointers(layer);
						}

						Matrix4f[] cameraMatrices = new Matrix4f[numViews];
						if (projectionViews != null) {
							initializeCameraMatrices(projectionViews, cameraMatrices);
						} else {
							System.arraycopy(lastCameraMatrix, 0, cameraMatrices, 0, numViews);
						}

						lastCameraMatrix = cameraMatrices;

						IntBuffer pImageIndex = stack.callocInt(1);
						assertXrSuccess(xrAcquireSwapchainImage(
								swapchain, null, pImageIndex
						), "AcquireSwapchainImage", null);
						int swapchainImageIndex = pImageIndex.get(0);

						syncActions(stack);
						recordRenderCommands(stack, frameState, swapchainImageIndex, cameraMatrices);

						var wiSwapchain = XrSwapchainImageWaitInfo.calloc(stack);
						wiSwapchain.type$Default();
						wiSwapchain.timeout(getSwapchainWaitTimeout());

						assertXrSuccess(xrWaitSwapchainImage(
								swapchain, wiSwapchain
						), "WaitSwapchainImage", null);

						submitRenderCommands();

						assertXrSuccess(xrReleaseSwapchainImage(
								swapchain, null
						), "ReleaseSwapchainImage", null);
					}

					var frameEnd = XrFrameEndInfo.calloc(stack);
					frameEnd.type$Default();
					frameEnd.displayTime(frameState.predictedDisplayTime());
					frameEnd.environmentBlendMode(getBlendMode());
					frameEnd.layerCount(layers != null ? layers.remaining() : 0);
					frameEnd.layers(layers);

					assertXrSuccess(xrEndFrame(session.xrSession, frameEnd), "EndFrame", null);
				}
			}
		}
	}

	/**
	 * Creates the projection matrix. This method will only be called from <i>initializeCameraMatrices</i>, so if you
	 * override that method, you could just return <b>null</b> in this method.
	 * @param fov The fov, queried from <i>xrLocateViews</i>
	 */
	protected abstract Matrix4f createProjectionMatrix(XrFovf fov);

	/**
	 * Initializes the camera matrices, and puts them in the <i>cameraMatrix</i> array
	 * @param projectionViews The projection views that will be passed to <i>xrEndFrame</i>
	 * @param cameraMatrix The array in which the camera matrices will be put. It will initially be filled with
	 *                     <b>nulls</b>.
	 */
	protected void initializeCameraMatrices(
			XrCompositionLayerProjectionView.Buffer projectionViews,
			Matrix4f[] cameraMatrix
	) {
		for (int index = 0; index < cameraMatrix.length; index++) {
			// If the position tracker is working, we should use it to create the camera matrix
			XrCompositionLayerProjectionView projectionView = projectionViews.get(index);

			Matrix4f projectionMatrix = createProjectionMatrix(projectionView.fov());

			Matrix4f viewMatrix = new Matrix4f();

			XrVector3f position = projectionView.pose().position$();
			XrQuaternionf orientation = projectionView.pose().orientation();

			viewMatrix.translationRotateScaleInvert(
					position.x(), position.y(), position.z(),
					orientation.x(), orientation.y(), orientation.z(), orientation.w(),
					1, 1, 1
			);

			cameraMatrix[index] = projectionMatrix.mul(viewMatrix);
		}
	}

	/**
	 * Chooses the active action sets that should be synchronized. You need to <i>attach</i> them to the session, if
	 * you haven't done so already. This method will only be called during <i>syncActions</i>, so you can return
	 * <b>null</b> if you decide to override <i>syncActions</i>.
	 */
	protected abstract XrActionSet[] chooseActiveActionSets();

	/**
	 * Calls <i>xrSyncActions</i>, using the action sets that were returned by <i>chooseActiveActionSets</i>
	 */
	protected void syncActions(MemoryStack stack) {
		XrActionSet[] desiredActionSets = chooseActiveActionSets();
		var activeActionSets = XrActiveActionSet.calloc(desiredActionSets.length, stack);
		for (int index = 0; index < desiredActionSets.length; index++) {
			//noinspection resource
			activeActionSets.get(index).actionSet(desiredActionSets[index]);
			//noinspection resource
			activeActionSets.get(index).subactionPath(XR_NULL_PATH);
		}

		var syncInfo = XrActionsSyncInfo.calloc(stack);
		syncInfo.type$Default();
		syncInfo.countActiveActionSets(1);
		syncInfo.activeActionSets(activeActionSets);

		assertXrSuccess(xrSyncActions(
				session.xrSession, syncInfo
		), "SyncActions", null);
	}

	/**
	 * Determines the <i>XrEnvironmentBlendMode</i> that will be used,
	 * by default <i>XR_ENVIRONMENT_BLEND_MODE_OPAQUE</i>
	 */
	protected int getBlendMode() {
		return XR_ENVIRONMENT_BLEND_MODE_OPAQUE;
	}

	/**
	 * Determines the <i>XrViewConfigurationType</i> that will be used,
	 * by default <i>XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO</i>
	 */
	protected int getViewConfigurationType() {
		return XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO;
	}

	/**
	 * Determines the number of views, by default 2 (one per eye)
	 */
	protected int getNumViews() {
		return 2;
	}

	/**
	 * The time to sleep (in milliseconds) whenever the session state is IDLE
	 */
	protected long getIdleSleepTime() {
		return 500L;
	}

	/**
	 * Used in XrCompositionLayerProjection.layerFlags
	 */
	protected int getLayerFlags() {
		return 0;
	}

	/**
	 * Determines the timeout passed to <i>XrSwapchainImageWaitInfo</i>
	 */
	protected long getSwapchainWaitTimeout() {
		return xr.boilerInstance.defaultTimeout;
	}

	/**
	 * This method will be called during each iteration of the session loop, after polling and handling events.
	 * You can do anything you want during this method, and you don't necessarily need to do anything at all.
	 */
	protected abstract void update();

	/**
	 * This method will be called whenever an OpenXR event is received. You can do anything you want during this method,
	 * and you don't necessarily need to do anything at all. When the event type is
	 * <i>XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED</i>, the session loop will respond to the event <i>before</i> calling
	 * this method.
	 */
	protected abstract void handleEvent(XrEventDataBuffer event);

	/**
	 * <p>
	 *   Waits until the render resources (like command buffers and fences) that will be needed next frame, will become
	 * 	 available.
	 * </p>
	 *
	 * This method will only be called during session loop iterations where the session state is either
	 * <i>SYNCHRONIZED</i>, <i>VISIBLE</i>, <i>FOCUSSED</i>, or <i>READY</i>. Note that this does <b>not</b> necessarily
	 * mean that a frame will be rendered during this iteration.
	 * @param stack You can use this memory stack if you want. It will be valid at least until this method returns.
	 */
	protected abstract void waitForRenderResources(MemoryStack stack);

	/**
	 * <p>
	 *     Records the command buffers that will render to the swapchain image with the given index. These command
	 *     buffers must <b>not</b> be submitted yet because this method is called after <i>xrAcquireSwapchainImage</i>,
	 *     but <b>before</b> <i>xrWaitSwapchainImage</i>.
	 * </p>
	 * @param stack You can use this memory stack if you want. It will be valid at least until this method returns.
	 * @param frameState The frame state that was passed to <i>xrWaitFrame</i>
	 * @param swapchainImageIndex The image of the swapchain image that was acquired
	 * @param cameraMatrices The camera matrices from <i>initializeCameraMatrices</i>, or the ones from last frame if
	 *                       the head tracking is currently having trouble
	 */
	protected abstract void recordRenderCommands(
			MemoryStack stack, XrFrameState frameState, int swapchainImageIndex, Matrix4f[] cameraMatrices
	);

	/**
	 * Submits the command buffers that were recorded during <i>recordRenderCommands</i>
	 */
	protected abstract void submitRenderCommands();
}
