package com.mehbur.irongolemtroll.loop;

import com.mehbur.irongolemtroll.config.TrollConfig;
import com.mehbur.irongolemtroll.physics.LaunchMath;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side core of the troll: tracks one loop per player/golem pair.
 *
 * <ol>
 *     <li>A player damages a golem: remember the player's Y and, if enabled, the golem
 *     throws the player up by {@code riseHeight}.</li>
 *     <li>If the player gets {@code riseHeight} above that Y within the trigger window,
 *     the golem is launched by the same height.</li>
 *     <li>The next hit starts over from step 1, which makes the loop endless.</li>
 * </ol>
 *
 * <p>Only velocity is changed; vanilla entity tracking sends the motion packets, so
 * clients need no mod. Everything runs on the server thread, so no locking is needed.
 */
public final class TrollLoopManager implements LoopControl {
	/** Extra height for the player throw, so reaching the trigger height is not a rounding coin flip. */
	private static final double PLAYER_LAUNCH_MARGIN = 0.25;

	private final Map<LoopKey, LoopState> loops = new HashMap<>();

	/** Hooked to {@code ServerLivingEntityEvents.AFTER_DAMAGE}. */
	public void onAfterDamage(LivingEntity entity, DamageSource source, float damageTaken, boolean blocked) {
		TrollConfig config = TrollConfig.get();
		if (!config.enabled || blocked || damageTaken <= 0.0f) {
			return;
		}
		// The killing blow ends the loop instead of starting one.
		if (!(entity instanceof IronGolem golem) || !golem.isAlive()) {
			return;
		}
		// getEntity() is the attacker, which also covers arrows and other projectiles.
		if (!(source.getEntity() instanceof ServerPlayer player) || !player.isAlive()) {
			return;
		}
		if (player.level() != golem.level() || player.distanceTo(golem) > config.maxDistance) {
			return;
		}

		LoopState state = loops.computeIfAbsent(new LoopKey(player.getUUID(), golem.getUUID()),
				key -> new LoopState(player, golem));
		state.onHit(player.level().getServer().getTickCount());

		if (config.golemLaunchesPlayer) {
			launch(player, config.riseHeight + PLAYER_LAUNCH_MARGIN);
		}
	}

	/** Hooked to {@code ServerTickEvents.END_SERVER_TICK}. */
	public void tick(MinecraftServer server) {
		if (loops.isEmpty()) {
			return;
		}

		TrollConfig config = TrollConfig.get();
		if (!config.enabled) {
			loops.clear();
			return;
		}

		int now = server.getTickCount();
		int cooldownTicks = config.cooldownResetSeconds * 20;

		Iterator<LoopState> it = loops.values().iterator();
		while (it.hasNext()) {
			LoopState state = it.next();
			if (shouldStop(state, config, now, cooldownTicks)) {
				it.remove();
				continue;
			}

			if (!state.armed) {
				continue;
			}
			if (now - state.lastHitTick > config.triggerWindowTicks) {
				// Window missed; the loop stays alive until the next hit or a stop condition.
				state.armed = false;
			} else if (state.player.getY() >= state.startY + config.riseHeight) {
				launch(state.golem, config.riseHeight);
				state.armed = false;
			}
		}
	}

	/** Stop conditions from the spec: death, removal, distance, or idling on the ground. */
	private static boolean shouldStop(LoopState state, TrollConfig config, int now, int cooldownTicks) {
		ServerPlayer player = state.player;
		IronGolem golem = state.golem;

		if (!player.isAlive() || player.isRemoved() || !golem.isAlive() || golem.isRemoved()) {
			return true;
		}
		if (player.level() != golem.level() || player.distanceTo(golem) > config.maxDistance) {
			return true;
		}
		return player.onGround() && now - state.lastHitTick >= cooldownTicks;
	}

	/**
	 * Sets the vertical velocity so the entity peaks {@code height} blocks above its
	 * current position. Horizontal velocity is kept, so the motion stays smooth.
	 */
	private static void launch(Entity entity, double height) {
		double vy = LaunchMath.velocityForHeight(height, entity.getGravity());
		Vec3 motion = entity.getDeltaMovement();
		entity.setDeltaMovement(motion.x, vy, motion.z);
		// Makes the entity tracker send the velocity packet right away (also to the player itself).
		entity.hurtMarked = true;
	}

	/**
	 * Drops every loop the entity takes part in, as player or as golem. Hooked to death,
	 * unload and disconnect events, so state goes away the moment an entity does instead
	 * of waiting for the next tick check.
	 */
	public void forget(Entity entity) {
		if (loops.isEmpty() || !(entity instanceof ServerPlayer || entity instanceof IronGolem)) {
			return;
		}
		UUID id = entity.getUUID();
		loops.keySet().removeIf(key -> key.player().equals(id) || key.golem().equals(id));
	}

	/** True while the given pair has an active loop. */
	public boolean hasLoop(Entity player, Entity golem) {
		return loops.containsKey(new LoopKey(player.getUUID(), golem.getUUID()));
	}

	@Override
	public int activeLoopCount() {
		return loops.size();
	}

	@Override
	public void stopAll() {
		loops.clear();
	}
}
