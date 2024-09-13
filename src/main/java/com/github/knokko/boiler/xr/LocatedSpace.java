package com.github.knokko.boiler.xr;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Represents the result of <i>xrLocateSpace</i>
 * @param position The resulting position, as JOML <i>Vector3f</i>.
 *                 Will be null if the <i>XR_SPACE_LOCATION_POSITION_VALID_BIT</i> was zero
 * @param orientation The resulting orientation, as JOML <i>Quaternionf</i>.
 *                    Will be null if the <i>XR_SPACE_LOCATION_ORIENTATION_VALID_BIT</i> was zero
 */
public record LocatedSpace(Vector3f position, Quaternionf orientation) {

	/**
	 * Creates and returns a <i>Matrix4f</i> by first translating the <i>position</i> and then rotating it with the
	 * <i>orientation</i>. If <i>position</i> is null, this method will return null. If <i>orientation</i> is null, the
	 * rotation will be skipped.
	 */
	public Matrix4f createMatrix() {
		Matrix4f matrix = null;
		if (position != null) {
			matrix = new Matrix4f().translate(position);

			if (orientation != null) matrix.rotate(orientation);
		}

		return matrix;
	}
}
