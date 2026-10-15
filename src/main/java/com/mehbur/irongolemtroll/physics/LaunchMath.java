package com.mehbur.irongolemtroll.physics;

/**
 * Computes the upward velocity that makes an entity rise an exact number of blocks.
 *
 * <p>Mirrors vanilla {@code LivingEntity#travelInAir}: every tick the entity first
 * moves by its velocity, then {@code vy = (vy - gravity) * 0.98}. Because of the drag
 * there is no closed form, so the peak height is simulated and the velocity is
 * found with a binary search.
 */
public final class LaunchMath {
	private static final double AIR_DRAG = 0.98;
	private static final double MAX_VELOCITY = 10.0;
	private static final int SEARCH_STEPS = 50;

	// Single-entry cache: the config rarely changes, so the same inputs repeat.
	private static double cachedHeight = Double.NaN;
	private static double cachedGravity = Double.NaN;
	private static double cachedVelocity;

	private LaunchMath() {
	}

	/**
	 * @param height  blocks to rise above the current position
	 * @param gravity the entity's gravity per tick (0.08 for players and golems)
	 * @return the vertical velocity whose peak is {@code height} blocks up
	 */
	public static synchronized double velocityForHeight(double height, double gravity) {
		if (height <= 0.0 || gravity <= 0.0) {
			return 0.0;
		}
		if (height == cachedHeight && gravity == cachedGravity) {
			return cachedVelocity;
		}

		double low = 0.0;
		double high = MAX_VELOCITY;
		for (int i = 0; i < SEARCH_STEPS; i++) {
			double mid = (low + high) / 2.0;
			if (peakHeight(mid, gravity) < height) {
				low = mid;
			} else {
				high = mid;
			}
		}

		cachedHeight = height;
		cachedGravity = gravity;
		cachedVelocity = high;
		return high;
	}

	/** Simulates vanilla air movement and returns the highest point reached. */
	static double peakHeight(double velocity, double gravity) {
		double y = 0.0;
		double vy = velocity;
		while (vy > 0.0) {
			y += vy;
			vy = (vy - gravity) * AIR_DRAG;
		}
		return y;
	}
}
