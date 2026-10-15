package com.mehbur.irongolemtroll.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LaunchMathTest {
	private static final double GRAVITY = 0.08;

	@Test
	void vanillaJumpPeakMatchesKnownValue() {
		// A vanilla jump (0.42 velocity) is known to peak at about 1.2522 blocks.
		assertEquals(1.2522, LaunchMath.peakHeight(0.42, GRAVITY), 1.0e-3);
	}

	@Test
	void velocityReachesRequestedHeight() {
		for (double height : new double[] {0.5, 1.0, 3.0, 3.25, 6.0, 20.0, 64.0}) {
			double v = LaunchMath.velocityForHeight(height, GRAVITY);
			double peak = LaunchMath.peakHeight(v, GRAVITY);
			assertTrue(peak >= height, "peak " + peak + " below " + height);
			// Discrete ticks make the peak jump in small steps, so allow a little overshoot.
			assertEquals(height, peak, 0.01, "height " + height);
		}
	}

	@Test
	void nonPositiveInputsGiveNoLaunch() {
		assertEquals(0.0, LaunchMath.velocityForHeight(0.0, GRAVITY));
		assertEquals(0.0, LaunchMath.velocityForHeight(3.0, 0.0));
	}
}
