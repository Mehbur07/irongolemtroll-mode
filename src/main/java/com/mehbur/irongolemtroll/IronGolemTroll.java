package com.mehbur.irongolemtroll;

import com.mehbur.irongolemtroll.climb.ClimbManager;
import com.mehbur.irongolemtroll.command.TrollCommand;
import com.mehbur.irongolemtroll.config.TrollConfig;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

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

	private static final ClimbManager CLIMBS = new ClimbManager();

	@Override
	public void onInitialize() {
		TrollConfig.load();

		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) ->
				CLIMBS.onAfterDamage(entity, source, damageTaken, blocked));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> CLIMBS.allowDamage(source));
		ServerTickEvents.END_SERVER_TICK.register(CLIMBS::tick);

		// Cleanup: no climb may outlive its golem or the server, and no held block its climb.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> CLIMBS.forget(entity));
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> CLIMBS.forget(entity));
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> CLIMBS.onEntityLoad(entity));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> CLIMBS.reset());

		TrollCommand.register(CLIMBS);

		LOGGER.info("Iron Golem Troll loaded (enabled={}).", TrollConfig.get().enabled);
	}

	/** The single climb manager, exposed for game tests. */
	public static ClimbManager climbs() {
		return CLIMBS;
	}
}
