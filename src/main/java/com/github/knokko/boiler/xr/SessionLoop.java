package com.github.knokko.boiler.xr;

import org.joml.Matrix4f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.openxr.*;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static com.github.knokko.boiler.xr.OpenXrFailureException.assertXrSuccess;
import static java.lang.Thread.sleep;
import static org.lwjgl.openxr.XR10.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public abstract class SessionLoop {

	protected final BoilerSession session;
	protected final XrBoiler xr;
	protected final XrSpace renderSpace;
	protected final XrSwapchain swapchain;
	protected final int width, height;

	private int state;
	private boolean isRunning;
	private boolean didRequestExit;
	private volatile boolean wantsToStop;

	public SessionLoop(
			BoilerSession session, XrSpace renderSpace, XrSwapchain swapchain, int width, int height
	) {
		this.session = session;
		this.xr = session.xr;
		this.renderSpace = renderSpace;
		this.swapchain = swapchain;
		this.width = width;
		this.height = height;
	}

	public void requestExit() {
		this.wantsToStop = true;
	}

	public int getState() {
		return state;
	}

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
							xr.boiler.queueFamilies().graphics().queues().get(0).vkQueue()
					), "QueueWaitIdle", "End of last frame");
					break;
				}

				if (this.state == XR_SESSION_STATE_STOPPING) {
					assertXrSuccess(xrEndSession(session.session), "EndSession", null);
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
					assertXrSuccess(xrRequestExitSession(session.session), "RequestExitSession", null);
					didRequestExit = true;
					continue;
				}

				if (this.state == XR_SESSION_STATE_READY && !isRunning) {
					var biSession = XrSessionBeginInfo.calloc(stack);
					biSession.type$Default();
					biSession.primaryViewConfigurationType(getViewConfigurationType());

					assertXrSuccess(xrBeginSession(
							session.session, biSession
					), "BeginSession", null);
					isRunning = true;
					continue;
				}

				if (this.state == XR_SESSION_STATE_SYNCHRONIZED || this.state == XR_SESSION_STATE_VISIBLE ||
						this.state == XR_SESSION_STATE_FOCUSED || this.state == XR_SESSION_STATE_READY
				) {
					var frameState = XrFrameState.calloc(stack);
					frameState.type$Default();

					assertXrSuccess(xrWaitFrame(
							session.session, null, frameState
					), "WaitFrame", null);
					assertXrSuccess(xrBeginFrame(
							session.session, null
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

						syncActions(stack);

						prepareRender(stack, frameState);

						IntBuffer pImageIndex = stack.callocInt(1);
						assertXrSuccess(xrAcquireSwapchainImage(
								swapchain, null, pImageIndex
						), "AcquireSwapchainImage", null);
						int swapchainImageIndex = pImageIndex.get(0);

						recordRenderCommands(stack, swapchainImageIndex, cameraMatrices);

						var wiSwapchain = XrSwapchainImageWaitInfo.calloc(stack);
						wiSwapchain.type$Default();
						wiSwapchain.timeout(getSwapchainWaitTimeout());

						assertXrSuccess(xrWaitSwapchainImage(
								swapchain, wiSwapchain
						), "WaitSwapchainImage", null);

						submitAndWaitRender();

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

					assertXrSuccess(xrEndFrame(session.session, frameEnd), "EndFrame", null);
				}
			}
		}
	}

	protected abstract Matrix4f createProjectionMatrix(XrFovf fov);

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

	protected abstract XrActionSet[] chooseActiveActionSets();

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
				session.session, syncInfo
		), "SyncActions", null);
	}

	protected int getBlendMode() {
		return XR_ENVIRONMENT_BLEND_MODE_OPAQUE;
	}

	protected int getViewConfigurationType() {
		return XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO;
	}

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

	protected long getSwapchainWaitTimeout() {
		return xr.boiler.defaultTimeout;
	}

	protected abstract void update();

	protected abstract void handleEvent(XrEventDataBuffer event);

	protected abstract void prepareRender(MemoryStack stack, XrFrameState frameState);

	protected abstract void recordRenderCommands(MemoryStack stack, int swapchainImageIndex, Matrix4f[] cameraMatrices);

	protected abstract void submitAndWaitRender();
}
