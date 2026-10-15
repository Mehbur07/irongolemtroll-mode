package com.mehbur.irongolemtroll.climb;

/**
 * The part of the climb manager that commands need: counting and stopping climbs.
 * Keeps the command layer independent from the climb implementation.
 */
public interface TrollControl {
	/** Number of golems that are climbing right now. */
	int activeCount();

	/** Ends every climb immediately; already placed blocks stay. */
	void stopAll();
}
