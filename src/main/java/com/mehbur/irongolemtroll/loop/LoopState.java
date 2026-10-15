package com.mehbur.irongolemtroll.loop;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.golem.IronGolem;

/**
 * Mutable state of one player/golem loop.
 *
 * <p>Holds direct entity references for cheap per-tick checks. The manager drops the
 * state as soon as either entity is removed, so the references never outlive it.
 */
final class LoopState {
	final ServerPlayer player;
	final IronGolem golem;

	/** Player Y at the moment of the last hit. */
	double startY;
	/** Server tick of the last hit. */
	int lastHitTick;
	/** True while waiting for the player to reach {@code startY + riseHeight}. */
	boolean armed;

	LoopState(ServerPlayer player, IronGolem golem) {
		this.player = player;
		this.golem = golem;
	}

	/** Starts a new rise attempt from the player's current height (mechanic step 1). */
	void onHit(int tick) {
		startY = player.getY();
		lastHitTick = tick;
		armed = true;
	}
}
