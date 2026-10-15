package com.mehbur.irongolemtroll.loop;

/**
 * The part of the loop manager that commands need: counting and stopping loops.
 * Keeps the command layer independent from the loop implementation.
 */
public interface LoopControl {
	/** Number of player/golem pairs that currently have loop state. */
	int activeLoopCount();

	/** Drops every active loop immediately. */
	void stopAll();
}
