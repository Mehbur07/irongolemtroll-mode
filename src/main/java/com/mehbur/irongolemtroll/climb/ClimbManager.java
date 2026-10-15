package com.mehbur.irongolemtroll.climb;

import com.mehbur.irongolemtroll.config.TrollConfig;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side core of the troll.
 *
 * <ol>
 *     <li>A player hits an iron golem.</li>
 *     <li>The golem takes an iron block in hand and pillars up {@code climbBlocks}
 *     blocks, leaving a permanent iron pillar.</li>
 *     <li>Until the climb is over the golem cannot hurt anyone.</li>
 *     <li>To hit it again the player has to get up there, and the next hit sends it
 *     even higher, so the loop can go on forever.</li>
 * </ol>
 *
 * <p>Everything runs on the server thread; vanilla entity tracking syncs motion,
 * blocks and the held block display, so clients need no mod.
 */
public final class ClimbManager implements TrollControl {
	private final Map<UUID, Climb> climbs = new HashMap<>();
	/** Held block displays to remove on the next tick, outside of any entity list iteration. */
	private final List<Entity> displaysToRemove = new ArrayList<>();

	/** Hooked to {@code ServerLivingEntityEvents.AFTER_DAMAGE}: a player hit starts a climb. */
	public void onAfterDamage(LivingEntity entity, DamageSource source, float damageTaken, boolean blocked) {
		TrollConfig config = TrollConfig.get();
		if (!config.enabled || blocked || damageTaken <= 0.0f) {
			return;
		}
		// The killing blow starts nothing.
		if (!(entity instanceof IronGolem golem) || !golem.isAlive() || climbs.containsKey(golem.getUUID())) {
			return;
		}
		// getEntity() is the attacker, which also covers arrows and other projectiles.
		if (!(source.getEntity() instanceof ServerPlayer player) || !player.isAlive()) {
			return;
		}
		if (player.level() != golem.level() || player.distanceTo(golem) > config.maxDistance) {
			return;
		}

		int now = player.level().getServer().getTickCount();
		climbs.put(golem.getUUID(), new Climb(golem, config.climbBlocks, now));
	}

	/** Hooked to {@code ServerLivingEntityEvents.ALLOW_DAMAGE}: a climbing golem deals no damage. */
	public boolean allowDamage(DamageSource source) {
		return !(source.getEntity() instanceof IronGolem golem && climbs.containsKey(golem.getUUID()));
	}

	/** Hooked to {@code ServerTickEvents.END_SERVER_TICK}. */
	public void tick(MinecraftServer server) {
		if (!displaysToRemove.isEmpty()) {
			for (Entity display : displaysToRemove) {
				if (!display.isRemoved()) {
					display.discard();
				}
			}
			displaysToRemove.clear();
		}
		if (climbs.isEmpty()) {
			return;
		}
		if (!TrollConfig.get().enabled) {
			stopAll();
			return;
		}

		int now = server.getTickCount();
		Iterator<Climb> it = climbs.values().iterator();
		while (it.hasNext()) {
			Climb climb = it.next();
			if (!climb.golem.isAlive() || climb.golem.isRemoved() || climb.tick(now)) {
				end(climb);
				it.remove();
			}
		}
	}

	/** Hooked to death and unload events: drops the climb of a golem that is gone. */
	public void forget(Entity entity) {
		if (entity instanceof IronGolem) {
			Climb climb = climbs.remove(entity.getUUID());
			if (climb != null) {
				end(climb);
			}
		}
	}

	/**
	 * Hooked to {@code ServerEntityEvents.ENTITY_LOAD}: a tagged held block that loads
	 * without a running climb is a leftover (e.g. saved during a server stop) and goes.
	 */
	public void onEntityLoad(Entity entity) {
		if (entity instanceof Display.BlockDisplay && entity.entityTags().contains(HeldBlockDisplay.TAG)) {
			for (Climb climb : climbs.values()) {
				if (climb.heldBlock() == entity) {
					return;
				}
			}
			displaysToRemove.add(entity);
		}
	}

	/** Hooked to {@code SERVER_STOPPED}: forget everything, held blocks are cleaned up on next load. */
	public void reset() {
		climbs.clear();
		displaysToRemove.clear();
	}

	/** True while the golem is climbing. */
	public boolean isClimbing(Entity golem) {
		return climbs.containsKey(golem.getUUID());
	}

	@Override
	public int activeCount() {
		return climbs.size();
	}

	@Override
	public void stopAll() {
		for (Climb climb : climbs.values()) {
			end(climb);
		}
		climbs.clear();
	}

	private void end(Climb climb) {
		climb.finish();
		if (climb.heldBlock() != null) {
			displaysToRemove.add(climb.heldBlock());
		}
	}
}
