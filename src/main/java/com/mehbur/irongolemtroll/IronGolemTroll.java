package com.mehbur.irongolemtroll;

import com.mehbur.irongolemtroll.command.TrollCommand;
import com.mehbur.irongolemtroll.config.TrollConfig;
import com.mehbur.irongolemtroll.loop.TrollLoopManager;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entrypoint of the Iron Golem Troll mod.
 *
 * <p>All gameplay logic runs on the logical server, so the mod works on a
 * dedicated server without being installed on clients, and is harmless when
 * a client does have it installed.
 */
public class IronGolemTroll implements ModInitializer {
	public static final String MOD_ID = "iron_golem_troll";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final TrollLoopManager LOOPS = new TrollLoopManager();

	@Override
	public void onInitialize() {
		TrollConfig.load();

		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) ->
				LOOPS.onAfterDamage(entity, source, damageTaken, blocked));
		ServerTickEvents.END_SERVER_TICK.register(LOOPS::tick);

		// Cleanup: no loop state may outlive its player, its golem or the server.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> LOOPS.forget(entity));
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> LOOPS.forget(entity));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LOOPS.forget(handler.getPlayer()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LOOPS.stopAll());

		TrollCommand.register(LOOPS);

		LOGGER.info("Iron Golem Troll loaded (enabled={}).", TrollConfig.get().enabled);
	}

	/** The single loop manager, exposed for game tests. */
	public static TrollLoopManager loops() {
		return LOOPS;
	}
}
