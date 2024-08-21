package com.github.knokko.boiler.culling;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static com.github.knokko.boiler.culling.FrustumCuller.isOnForwardPlane;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestFrustumCuller {

	@Test
	public void testIsOnForwardPlaneUp() {
		var box = new FrustumCuller.AABB(10f, 20f, 30f, 40f, 50f, 60f);

		for (float y : new float[]{-100f, -10f, 0f, 10f, 19f, 20f, 25f, 45f, 49f}) {
			assertTrue(isOnForwardPlane(box, new FrustumCuller.Plane(new Vector3f(0f, y, 0f), new Vector3f(0f, 1f, 0f))));
		}
		for (float y : new float[]{51f, 55f, 100f}) {
			assertFalse(isOnForwardPlane(box, new FrustumCuller.Plane(new Vector3f(0f, y, 0f), new Vector3f(0f, 1f, 0f))));
		}
	}

	@Test
	public void testIsOnForwardPlaneDown() {
		var box = new FrustumCuller.AABB(10f, 20f, 30f, 40f, 50f, 60f);

		for (float y : new float[]{-100f, -10f, 0f, 10f, 19f}) {
			assertFalse(isOnForwardPlane(box, new FrustumCuller.Plane(new Vector3f(0f, y, 0f), new Vector3f(0f, -1f, 0f))));
		}
		for (float y : new float[]{25f, 45f, 49f, 50f, 51f, 55f, 100f}) {
			assertTrue(isOnForwardPlane(box, new FrustumCuller.Plane(new Vector3f(0f, y, 0f), new Vector3f(0f, -1f, 0f))));
		}
	}

	@Test
	public void testIsOnForwardPlaneEast() {
		var box = new FrustumCuller.AABB(10f, 20f, 30f, 40f, 50f, 60f);

		for (float x : new float[]{-100f, -10f, 0f, 9f, 20f, 25f, 39f}) {
			assertTrue(isOnForwardPlane(box, new FrustumCuller.Plane(new Vector3f(x, 0f, 0f), new Vector3f(1f, 0f, 0f))));
		}
		for (float x : new float[]{41f, 55f, 100f}) {
			assertFalse(isOnForwardPlane(box, new FrustumCuller.Plane(new Vector3f(x, 0f, 0f), new Vector3f(1f, 0f, 0f))));
		}
	}

	@Test
	public void testIsOnForwardPlaneWest() {
		var box = new FrustumCuller.AABB(10f, 20f, 30f, 40f, 50f, 60f);

		for (float x : new float[]{-100f, -10f, 0f, 9f}) {
			assertFalse(isOnForwardPlane(box, new FrustumCuller.Plane(new Vector3f(x, 0f, 0f), new Vector3f(-1f, 0f, 0f))));
		}
		for (float x : new float[]{11f, 25f, 39f, 40f, 41f, 55f, 100f}) {
			assertTrue(isOnForwardPlane(box, new FrustumCuller.Plane(new Vector3f(x, 0f, 0f), new Vector3f(-1f, 0f, 0f))));
		}
	}

	@Test
	public void testShouldCullAABBCameraAtOrigin() {
		var frustum1 = new FrustumCuller(
				new Vector3f(), new Vector3f(0f, 0f, -1f), new Vector3f(0f, 1f, 0f),
				1f, 45f, 0.1f, 200f
		);
		var frustum2 = new FrustumCuller(new Vector3f(), 0f, 0f, 1f, 45f, 0.1f, 200f);

		for (var frustum : new FrustumCuller[]{frustum1, frustum2}) {

			// Completely behind the camera
			assertTrue(frustum.shouldCullAABB(new FrustumCuller.AABB(-10f, -10f, 5f, 10f, 10f, 10f)));

			// The camera is inside the AABB
			assertFalse(frustum.shouldCullAABB(new FrustumCuller.AABB(-10f, -10f, -5f, 10f, 10f, 10f)));

			// Completely in front of the camera
			assertFalse(frustum.shouldCullAABB(new FrustumCuller.AABB(-10f, -10f, -15f, 10f, 10f, -5f)));

			// Too far to the left
			assertTrue(frustum.shouldCullAABB(new FrustumCuller.AABB(-100f, -10f, -10f, -50f, 10f, -5f)));

			// Too far to the right
			assertTrue(frustum.shouldCullAABB(new FrustumCuller.AABB(100f, -10f, -10f, 200f, 10f, -5f)));

			// Sticks out on the left
			assertFalse(frustum.shouldCullAABB(new FrustumCuller.AABB(-100f, -10f, -10f, -2f, 10f, -5f)));

			// Sticks out on the right
			assertFalse(frustum.shouldCullAABB(new FrustumCuller.AABB(2f, -10f, -10f, 200f, 10f, -5)));

			// Too far to the bottom
			assertTrue(frustum.shouldCullAABB(new FrustumCuller.AABB(-10f, -100f, -10f, 10f, -50f, -5f)));

			// Too far to the top
			assertTrue(frustum.shouldCullAABB(new FrustumCuller.AABB(-10f, 100f, -10f, 10f, 200f, -5f)));

			// Sticks out on the bottom
			assertFalse(frustum.shouldCullAABB(new FrustumCuller.AABB(-10f, -100f, -10f, 10f, -2f, -5f)));

			// Sticks out on the top
			assertFalse(frustum.shouldCullAABB(new FrustumCuller.AABB(-10f, 2f, -10f, 10f, 200f, -5)));
		}
	}

	@Test
	public void testShouldCullAABBCameraTurnedRight() {
		var frustum1 = new FrustumCuller(
				new Vector3f(0f, 50f, 100f), new Vector3f(1f, 0f, 0f), new Vector3f(0f, 1f, 0f),
				1.5f, 60f, 1f, 100f
		);
		var frustum2 = new FrustumCuller(
				new Vector3f(0f, 50f, 100f), 90f, 0f, 1.5f, 60f, 1f, 100f
		);

		for (var frustum : new FrustumCuller[]{frustum1, frustum2}) {

			// In front of the camera
			assertFalse(frustum.shouldCullAABB(new FrustumCuller.AABB(20f, 30f, 80f, 30f, 80f, 110f)));

			// Behind the camera
			assertTrue(frustum.shouldCullAABB(new FrustumCuller.AABB(-20f, 30f, 80f, -10f, 80f, 110f)));

			// Too far to the left
			assertTrue(frustum.shouldCullAABB(new FrustumCuller.AABB(20f, 30f, 20f, 30f, 80f, 40f)));

			// Too far to the right
			assertTrue(frustum.shouldCullAABB(new FrustumCuller.AABB(20f, 30f, 220f, 30f, 80f, 240f)));

			// Too far above the camera
			assertTrue(frustum.shouldCullAABB(new FrustumCuller.AABB(20f, 130f, 80f, 30f, 180f, 110f)));
		}
	}

	@Test
	public void testShouldCullAABBCameraLookingStraightDown() {
		var frustum1 = new FrustumCuller(
				new Vector3f(100f, 0f, 0f), new Vector3f(0f, -1f, 0f), new Vector3f(0, 0f, -1f),
				2f, 30f, 0.01f, 1000f
		);

		var frustum2 = new FrustumCuller(
				new Vector3f(100f, 0f, 0f), 0f, -90f, 2f, 30f, 0.01f, 1000f
		);

		for (var frustum : new FrustumCuller[]{frustum1, frustum2}) {

			// In front of the camera
			assertFalse(frustum.shouldCullAABB(new FrustumCuller.AABB(95f, -20f, -5f, 105f, -10f, 5f)));

			// Behind the camera
			assertTrue(frustum.shouldCullAABB(new FrustumCuller.AABB(95f, 20f, -5f, 105f, 30f, 5f)));

			// Left of the camera
			assertTrue(frustum.shouldCullAABB(new FrustumCuller.AABB(65f, -20f, -5f, 75f, -10f, 5f)));

			// Right of the camera
			assertTrue(frustum.shouldCullAABB(new FrustumCuller.AABB(135f, -20f, -5f, 145f, -10f, 5f)));

			// 'Above' the camera
			assertTrue(frustum.shouldCullAABB(new FrustumCuller.AABB(95f, -20f, -35f, 105f, -10f, -25f)));

			// 'Below' the camera
			assertTrue(frustum.shouldCullAABB(new FrustumCuller.AABB(95f, -20f, 35f, 105f, -10f, 45f)));
		}
	}
}
