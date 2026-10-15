package com.mehbur.irongolemtroll.test;

import com.mehbur.irongolemtroll.IronGolemTroll;
import com.mehbur.irongolemtroll.config.TrollConfig;
import com.mehbur.irongolemtroll.loop.TrollLoopManager;
import com.mehbur.irongolemtroll.physics.LaunchMath;
import com.mojang.authlib.GameProfile;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-world tests for the loop. Fake players do not tick, so the tests move them by
 * hand to stand in for what a real client would report.
 *
 * <p>Tests run in parallel and share one loop manager, so every test uses its own
 * fake player and only checks its own pairs.
 */
public class TrollLoopGameTest {
	private static final AtomicInteger PLAYER_COUNTER = new AtomicInteger();

	@GameTest(maxTicks = 100)
	public void fullLoopLaunchesPlayerThenGolem(GameTestHelper helper) {
		TrollConfig config = TrollConfig.get();
		IronGolem golem = spawnGolem(helper, 2);
		FakePlayer player = spawnPlayer(helper, 4.5, 1, 2.5);
		double golemStartY = golem.getY();

		hit(helper, player, golem, 1.0f);

		helper.assertTrue(loops().hasLoop(player, golem), "hit did not start a loop");
		double expected = LaunchMath.velocityForHeight(config.riseHeight + 0.25, player.getGravity());
		helper.assertTrue(Math.abs(player.getDeltaMovement().y - expected) < 1.0e-6,
				"player was not thrown up, vy=" + player.getDeltaMovement().y);

		// Stand-in for the client reporting the player at the top of the throw.
		player.setPos(player.getX(), player.getY() + config.riseHeight + 0.1, player.getZ());

		double[] peak = {golemStartY};
		helper.succeedWhen(() -> {
			peak[0] = Math.max(peak[0], golem.getY());
			double rise = peak[0] - golemStartY;
			// Wait until the golem has passed its highest point.
			helper.assertTrue(rise > 1.0 && golem.getDeltaMovement().y < 0, "golem still rising, rise=" + rise);
			helper.assertTrue(Math.abs(rise - config.riseHeight) < 0.35, "golem rose " + rise + " blocks");
		});
	}

	@GameTest(maxTicks = 100)
	public void golemStaysWhenPlayerDoesNotRise(GameTestHelper helper) {
		IronGolem golem = spawnGolem(helper, 2);
		FakePlayer player = spawnPlayer(helper, 4.5, 1, 2.5);
		double golemStartY = golem.getY();

		hit(helper, player, golem, 1.0f);

		helper.runAfterDelay(TrollConfig.get().triggerWindowTicks + 5, () -> {
			helper.assertTrue(golem.getY() < golemStartY + 0.5, "golem was launched without the player rising");
			helper.assertTrue(loops().hasLoop(player, golem), "loop ended although no stop condition was met");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void loopStopsWhenPlayerWalksAway(GameTestHelper helper) {
		IronGolem golem = spawnGolem(helper, 2);
		FakePlayer player = spawnPlayer(helper, 4.5, 1, 2.5);

		hit(helper, player, golem, 1.0f);
		player.setPos(player.getX() + TrollConfig.get().maxDistance + 5, player.getY(), player.getZ());

		helper.runAfterDelay(2, () -> {
			helper.assertTrue(!loops().hasLoop(player, golem), "loop survived max distance");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void loopStopsAfterIdlingOnGround(GameTestHelper helper) {
		IronGolem golem = spawnGolem(helper, 2);
		FakePlayer player = spawnPlayer(helper, 4.5, 1, 2.5);
		int cooldownTicks = TrollConfig.get().cooldownResetSeconds * 20;

		hit(helper, player, golem, 1.0f);
		player.setOnGround(true);

		helper.runAfterDelay(cooldownTicks - 10, () ->
				helper.assertTrue(loops().hasLoop(player, golem), "loop ended before the cooldown"));
		helper.runAfterDelay(cooldownTicks + 5, () -> {
			helper.assertTrue(!loops().hasLoop(player, golem), "loop survived idling on the ground");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void loopStopsWhenGolemDies(GameTestHelper helper) {
		IronGolem golem = spawnGolem(helper, 2);
		FakePlayer player = spawnPlayer(helper, 4.5, 1, 2.5);

		hit(helper, player, golem, 1.0f);
		golem.kill(helper.getLevel());

		// The death event must clear the state at once, not on the next tick check.
		helper.assertTrue(!loops().hasLoop(player, golem), "loop survived golem death");
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void loopStopsWhenGolemIsRemoved(GameTestHelper helper) {
		IronGolem golem = spawnGolem(helper, 2);
		FakePlayer player = spawnPlayer(helper, 4.5, 1, 2.5);

		hit(helper, player, golem, 1.0f);
		golem.discard(); // same path as /kill-less removal or a chunk unload

		helper.assertTrue(!loops().hasLoop(player, golem), "loop survived golem removal");
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void loopStopsWhenPlayerDies(GameTestHelper helper) {
		IronGolem golem = spawnGolem(helper, 2);
		FakePlayer player = spawnPlayer(helper, 4.5, 1, 2.5);

		hit(helper, player, golem, 1.0f);
		// Fake players ignore damage, so fire the death event the way vanilla death would.
		ServerLivingEntityEvents.AFTER_DEATH.invoker().afterDeath(player, player.damageSources().fall());

		helper.assertTrue(!loops().hasLoop(player, golem), "loop survived player death");
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void loopStopsWhenPlayerDisconnects(GameTestHelper helper) {
		IronGolem golem = spawnGolem(helper, 2);
		FakePlayer player = spawnPlayer(helper, 4.5, 1, 2.5);

		hit(helper, player, golem, 1.0f);
		ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(player.connection, helper.getLevel().getServer());

		helper.assertTrue(!loops().hasLoop(player, golem), "loop survived player disconnect");
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void forgettingOnePairKeepsOthers(GameTestHelper helper) {
		IronGolem golemA = spawnGolem(helper, 1);
		IronGolem golemB = spawnGolem(helper, 4);
		FakePlayer player = spawnPlayer(helper, 2.5, 1, 4.5);

		hit(helper, player, golemA, 1.0f);
		hit(helper, player, golemB, 1.0f);
		golemA.discard();

		helper.assertTrue(!loops().hasLoop(player, golemA), "removed golem kept its loop");
		helper.assertTrue(loops().hasLoop(player, golemB), "other golem lost its loop");
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void killingBlowStartsNoLoop(GameTestHelper helper) {
		IronGolem golem = spawnGolem(helper, 2);
		FakePlayer player = spawnPlayer(helper, 4.5, 1, 2.5);

		hit(helper, player, golem, 1000.0f);

		helper.assertTrue(!golem.isAlive(), "golem survived a 1000 damage hit");
		helper.assertTrue(!loops().hasLoop(player, golem), "killing blow started a loop");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void pairsAreIndependent(GameTestHelper helper) {
		IronGolem golemA = spawnGolem(helper, 1);
		IronGolem golemB = spawnGolem(helper, 4);
		FakePlayer playerA = spawnPlayer(helper, 1.5, 1, 4.5);
		FakePlayer playerB = spawnPlayer(helper, 4.5, 1, 4.5);
		double startA = golemA.getY();
		double startB = golemB.getY();

		hit(helper, playerA, golemA, 1.0f);
		hit(helper, playerB, golemB, 1.0f);
		helper.assertTrue(loops().hasLoop(playerA, golemA) && loops().hasLoop(playerB, golemB), "both pairs need a loop");
		helper.assertTrue(!loops().hasLoop(playerA, golemB), "pairs got mixed up");

		// Only player A rises, so only golem A may fly.
		playerA.setPos(playerA.getX(), playerA.getY() + TrollConfig.get().riseHeight + 0.1, playerA.getZ());

		helper.runAfterDelay(8, () -> {
			helper.assertTrue(golemA.getY() > startA + 1.0, "golem A did not rise");
			helper.assertTrue(golemB.getY() < startB + 0.5, "golem B rose without its player rising");
			helper.succeed();
		});
	}

	private static TrollLoopManager loops() {
		return IronGolemTroll.loops();
	}

	/** Spawns a golem on a stone floor at relative (x, 1, 2). */
	private static IronGolem spawnGolem(GameTestHelper helper, int x) {
		for (int fx = 0; fx < 7; fx++) {
			for (int fz = 0; fz < 7; fz++) {
				helper.setBlock(fx, 0, fz, Blocks.STONE);
			}
		}
		IronGolem golem = helper.spawn(EntityTypes.IRON_GOLEM, new BlockPos(x, 1, 2));
		golem.setPlayerCreated(true); // player-built golems never fight players back
		return golem;
	}

	/** Creates a fresh fake player at a relative position inside the test area. */
	private static FakePlayer spawnPlayer(GameTestHelper helper, double x, double y, double z) {
		int n = PLAYER_COUNTER.incrementAndGet();
		FakePlayer player = FakePlayer.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "igt_test_" + n));
		player.snapTo(helper.absoluteVec(new Vec3(x, y, z)));
		player.setDeltaMovement(Vec3.ZERO);
		player.setOnGround(false);
		return player;
	}

	private static void hit(GameTestHelper helper, FakePlayer player, IronGolem golem, float damage) {
		golem.invulnerableTime = 0;
		golem.hurtServer(helper.getLevel(), player.damageSources().playerAttack(player), damage);
	}
}
