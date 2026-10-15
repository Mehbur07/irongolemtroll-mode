package com.mehbur.irongolemtroll.test;

import com.mehbur.irongolemtroll.IronGolemTroll;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

/**
 * Visual check in a real client (run with {@code ./gradlew runClientGameTest}): the player
 * stands on a 3 block pillar, left-clicks a golem, and screenshots show it walking over
 * with an iron block in hand and pillaring up next to the player.
 */
public class TrollVisualClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			server.runCommand("gamemode creative @a");
			server.runCommand("time set noon");
			server.runCommand("weather clear");

			BlockPos base = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().blockPosition());
			server.runCommand("fill %d %d %d %d %d %d minecraft:oak_planks".formatted(
					base.getX(), base.getY(), base.getZ(), base.getX(), base.getY() + 2, base.getZ()));
			server.runCommand(String.format(Locale.ROOT, "tp @a %.1f %d %.1f -90 40",base.getX() + 0.5, base.getY() + 3, base.getZ() + 0.5));

			UUID golemId = server.computeOnServer(s -> {
				ServerLevel level = s.overworld();
				IronGolem golem = EntityTypes.IRON_GOLEM.create(level, EntitySpawnReason.COMMAND);
				golem.snapTo(base.getX() + 3.5, base.getY(), base.getZ() + 0.5, 90.0f, 0.0f);
				golem.setPlayerCreated(true);
				level.addFreshEntity(golem);
				return golem.getUUID();
			});

			world.getConnection().waitForChunksRender();
			context.waitTicks(40);
			aimAtGolem(context, server, golemId);
			shot(context, "01_before_hit");

			// A real left click, the same path as hitting the golem in game.
			context.getInput().pressKey(options -> options.keyAttack);
			context.waitTick();
			boolean started = server.computeOnServer(s -> {
				Entity golem = s.overworld().getEntity(golemId);
				return golem != null && IronGolemTroll.climbs().isClimbing(golem);
			});
			IronGolemTroll.LOGGER.info("VISUAL left click started the troll: {}", started);

			for (int i = 0; i < 14; i++) {
				context.waitTicks(4);
				aimAtGolem(context, server, golemId);
				shot(context, "%02d_after_%d_ticks".formatted(i + 2, (i + 1) * 4 + 1));
			}

			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(2);
			shot(context, "16_third_person_end");

			String result = server.computeOnServer(s -> {
				ServerLevel level = s.overworld();
				Entity golem = level.getEntity(golemId);
				ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
				int iron = 0;
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						for (int dy = 0; dy < 8; dy++) {
							if (level.getBlockState(base.offset(dx, dy, dz)).is(net.minecraft.world.level.block.Blocks.IRON_BLOCK)) {
								iron++;
							}
						}
					}
				}
				return "golem=" + (golem == null ? "gone" : golem.position()) + " player=" + player.position()
						+ " climbing=" + (golem != null && IronGolemTroll.climbs().isClimbing(golem)) + " ironBlocks=" + iron;
			});
			IronGolemTroll.LOGGER.info("VISUAL end state: {}", result);
		}
	}

	/** Points the client camera at the golem's chest, like a player aiming at it. */
	private static void aimAtGolem(ClientGameTestContext context, TestServerContext server, UUID golemId) {
		float[] yawPitch = server.computeOnServer(s -> {
			Entity golem = s.overworld().getEntity(golemId);
			ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
			if (golem == null) {
				return new float[] {player.getYRot(), player.getXRot()};
			}
			Vec3 eye = player.getEyePosition();
			Vec3 to = golem.position().add(0.0, golem.getBbHeight() / 2.0, 0.0).subtract(eye);
			float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
			float pitch = (float) -Math.toDegrees(Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)));
			return new float[] {yaw, pitch};
		});
		context.getInput().lookAt(yawPitch[0], yawPitch[1]);
	}

	private static void shot(ClientGameTestContext context, String name) {
		Path path = context.takeScreenshot(name);
		IronGolemTroll.LOGGER.info("VISUAL screenshot {}", path);
	}
}
