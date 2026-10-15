package com.mehbur.irongolemtroll.test;

import com.mehbur.irongolemtroll.IronGolemTroll;
import com.mehbur.irongolemtroll.climb.ClimbManager;
import com.mojang.authlib.GameProfile;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-world tests for the troll golem, using fake players as attackers.
 *
 * <p>Tests run in parallel and share one climb manager, so every test uses its own
 * golems and only checks those. {@code skyAccess} keeps the test frame from adding a
 * barrier ceiling above the climbing golem.
 */
public class TrollClimbGameTest {
	private static final AtomicInteger PLAYER_COUNTER = new AtomicInteger();
	private static final int CLIMB_BLOCKS = 3;

	@GameTest(maxTicks = 300, skyAccess = true)
	public void golemWalksToPlayerOnPillarAndClimbsBesideThem(GameTestHelper helper) {
		// The situation from the screenshot: the player stands on a 3 block pillar.
		for (int y = 1; y <= 3; y++) {
			helper.setBlock(5, y, 2, Blocks.OAK_PLANKS);
		}
		floor(helper);
		IronGolem golem = spawnGolem(helper, 1);
		FakePlayer player = spawnPlayer(helper, 5.5, 4, 2.5);
		BlockPos column = player.blockPosition().below(3).west(); // ground cell next to the pillar, golem side

		hit(helper, player, golem, 1.0f);
		helper.assertTrue(climbs().isClimbing(golem), "hit did not start the troll");

		helper.succeedWhen(() -> {
			assertClimbDone(helper, golem);
			assertPillar(helper, column, CLIMB_BLOCKS);
			helper.assertTrue(Math.abs(golem.getY() - player.getY()) < 0.01, "golem did not end at the player's height");
			// Flush against the face of the player's pillar.
			double gap = (column.getX() + 1) - golem.getBoundingBox().maxX;
			helper.assertTrue(gap >= 0.0 && gap < 0.05, "golem is " + gap + " blocks away from the player's pillar");
		});
	}

	@GameTest(maxTicks = 200, skyAccess = true)
	public void golemNextToPlayerClimbsThreeIronBlocks(GameTestHelper helper) {
		floor(helper);
		IronGolem golem = spawnGolem(helper, 2);
		FakePlayer player = spawnPlayer(helper, 4.5, 1, 2.5);
		BlockPos column = player.blockPosition().west();
		double startY = golem.getY();

		hit(helper, player, golem, 1.0f);

		helper.succeedWhen(() -> {
			assertClimbDone(helper, golem);
			assertPillar(helper, column, CLIMB_BLOCKS);
			helper.assertTrue(Math.abs(golem.getY() - (startY + CLIMB_BLOCKS)) < 0.01,
					"golem ended at +" + (golem.getY() - startY) + " instead of +" + CLIMB_BLOCKS);
		});
	}

	@GameTest(maxTicks = 400, skyAccess = true)
	public void playerBuildsHigherAndGolemFollows(GameTestHelper helper) {
		floor(helper);
		IronGolem golem = spawnGolem(helper, 2);
		FakePlayer player = spawnPlayer(helper, 4.5, 1, 2.5);
		BlockPos column = player.blockPosition().west();

		hit(helper, player, golem, 1.0f);
		helper.startSequence()
				.thenWaitUntil(() -> assertClimbDone(helper, golem))
				.thenExecute(() -> {
					// The player pillars up 3 blocks and hits the golem again from up there.
					for (int y = 1; y <= 3; y++) {
						helper.setBlock(4, y, 2, Blocks.OAK_PLANKS);
					}
					player.setPos(player.getX(), player.getY() + 3, player.getZ());
					hit(helper, player, golem, 1.0f);
					helper.assertTrue(climbs().isClimbing(golem), "second hit did not start a climb");
				})
				.thenWaitUntil(() -> assertClimbDone(helper, golem))
				.thenExecute(() -> {
					assertPillar(helper, column, 2 * CLIMB_BLOCKS);
					helper.assertTrue(Math.abs(golem.getY() - (player.getY() + 3)) < 0.01, "golem is not 3 above the player");
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 200, skyAccess = true)
	public void hitDuringClimbDoesNotExtendIt(GameTestHelper helper) {
		floor(helper);
		IronGolem golem = spawnGolem(helper, 2);
		FakePlayer player = spawnPlayer(helper, 4.5, 1, 2.5);
		BlockPos column = player.blockPosition().west();

		hit(helper, player, golem, 1.0f);
		helper.runAfterDelay(15, () -> hit(helper, player, golem, 1.0f));

		helper.succeedWhen(() -> {
			assertClimbDone(helper, golem);
			assertPillar(helper, column, CLIMB_BLOCKS);
		});
	}

	@GameTest(maxTicks = 200, skyAccess = true)
	public void golemCannotHurtUntilClimbIsOver(GameTestHelper helper) {
		floor(helper);
		IronGolem golem = spawnGolem(helper, 2);
		FakePlayer player = spawnPlayer(helper, 4.5, 1, 2.5);
		Pig pig = helper.spawn(EntityTypes.PIG, new BlockPos(5, 1, 5));
		ServerLevel level = helper.getLevel();

		hit(helper, player, golem, 1.0f);
		float before = pig.getHealth();
		helper.assertTrue(!golem.doHurtTarget(level, pig), "troll golem landed a hit");
		helper.assertTrue(pig.getHealth() == before, "troll golem dealt damage");

		helper.succeedWhen(() -> {
			assertClimbDone(helper, golem);
			pig.invulnerableTime = 0;
			helper.assertTrue(golem.doHurtTarget(level, pig), "golem still cannot attack after the climb");
		});
	}

	@GameTest(maxTicks = 200, skyAccess = true)
	public void ceilingStopsClimbEarly(GameTestHelper helper) {
		floor(helper);
		IronGolem golem = spawnGolem(helper, 2);
		FakePlayer player = spawnPlayer(helper, 4.5, 1, 2.5);
		BlockPos column = player.blockPosition().west();
		// Golem is 2.7 tall: from y=1 one hop fits under y=5, from y=2 the next does not.
		for (int x = 1; x <= 4; x++) {
			for (int z = 1; z <= 3; z++) {
				helper.setBlock(x, 5, z, Blocks.STONE);
			}
		}

		hit(helper, player, golem, 1.0f);

		helper.succeedWhen(() -> {
			assertClimbDone(helper, golem);
			assertPillar(helper, column, 1);
		});
	}

	@GameTest(maxTicks = 200, skyAccess = true)
	public void heldIronBlockIsShownThenRemoved(GameTestHelper helper) {
		floor(helper);
		IronGolem golem = spawnGolem(helper, 2);
		FakePlayer player = spawnPlayer(helper, 4.5, 1, 2.5);

		hit(helper, player, golem, 1.0f);
		Display.BlockDisplay held = heldBlock(golem);
		helper.assertTrue(held != null, "golem holds no block display");
		helper.assertTrue(held.getBlockState().is(Blocks.IRON_BLOCK), "held block is not iron");

		helper.startSequence()
				.thenWaitUntil(() -> assertClimbDone(helper, golem))
				.thenExecuteAfter(2, () -> {
					helper.assertTrue(held.isRemoved(), "held block stayed after the climb");
					helper.assertTrue(golem.getPassengers().isEmpty(), "golem still carries something");
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 40, skyAccess = true)
	public void golemDeathEndsClimbAndRemovesBlock(GameTestHelper helper) {
		floor(helper);
		IronGolem golem = spawnGolem(helper, 2);
		FakePlayer player = spawnPlayer(helper, 4.5, 1, 2.5);

		hit(helper, player, golem, 1.0f);
		Display.BlockDisplay held = heldBlock(golem);
		golem.kill(helper.getLevel());

		helper.assertTrue(!climbs().isClimbing(golem), "climb survived golem death");
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(held == null || held.isRemoved(), "held block survived golem death");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 20, skyAccess = true)
	public void killingBlowStartsNoClimb(GameTestHelper helper) {
		floor(helper);
		IronGolem golem = spawnGolem(helper, 2);
		FakePlayer player = spawnPlayer(helper, 4.5, 1, 2.5);

		hit(helper, player, golem, 1000.0f);

		helper.assertTrue(!golem.isAlive(), "golem survived a 1000 damage hit");
		helper.assertTrue(!climbs().isClimbing(golem), "killing blow started a climb");
		helper.succeed();
	}

	@GameTest(maxTicks = 20, skyAccess = true)
	public void farAwayHitStartsNoClimb(GameTestHelper helper) {
		floor(helper);
		IronGolem golem = spawnGolem(helper, 2);
		FakePlayer player = spawnPlayer(helper, 4.5, 1, 2.5);
		player.setPos(player.getX() + 20, player.getY(), player.getZ());

		hit(helper, player, golem, 1.0f);

		helper.assertTrue(!climbs().isClimbing(golem), "hit from beyond maxDistance started a climb");
		helper.succeed();
	}

	@GameTest(maxTicks = 200, skyAccess = true)
	public void golemsClimbIndependently(GameTestHelper helper) {
		floor(helper);
		IronGolem golemA = spawnGolem(helper, 1);
		IronGolem golemB = spawnGolem(helper, 5);
		FakePlayer player = spawnPlayer(helper, 1.5, 1, 5.5);
		BlockPos columnB = golemB.blockPosition();

		hit(helper, player, golemA, 1.0f);
		helper.assertTrue(climbs().isClimbing(golemA) && !climbs().isClimbing(golemB), "wrong golem climbs");

		helper.succeedWhen(() -> {
			assertClimbDone(helper, golemA);
			assertPillar(helper, player.blockPosition().north(), CLIMB_BLOCKS);
			assertPillar(helper, columnB, 0);
		});
	}

	@GameTest(maxTicks = 20, skyAccess = true)
	public void leftoverHeldBlockIsRemovedOnLoad(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Display.BlockDisplay leftover = EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.LOAD);
		// Same as a held block saved during a server stop and loaded again later.
		leftover.addTag(IronGolemTroll.MOD_ID + ".held_block");
		leftover.snapTo(helper.absoluteVec(new Vec3(2.5, 1, 2.5)));
		level.addFreshEntity(leftover);

		helper.runAfterDelay(2, () -> {
			helper.assertTrue(leftover.isRemoved(), "leftover held block was not removed");
			helper.succeed();
		});
	}

	private static ClimbManager climbs() {
		return IronGolemTroll.climbs();
	}

	private static void assertClimbDone(GameTestHelper helper, IronGolem golem) {
		helper.assertTrue(!climbs().isClimbing(golem), "golem is still busy");
		helper.assertTrue(golem.onGround(), "golem has not landed");
	}

	/** Asserts exactly {@code count} iron blocks stacked from {@code column} upwards. */
	private static void assertPillar(GameTestHelper helper, BlockPos column, int count) {
		ServerLevel level = helper.getLevel();
		for (int i = 0; i < count; i++) {
			helper.assertTrue(level.getBlockState(column.above(i)).is(Blocks.IRON_BLOCK),
					"no iron block at pillar level " + i + " of " + column);
		}
		helper.assertTrue(!level.getBlockState(column.above(count)).is(Blocks.IRON_BLOCK), "pillar is higher than " + count);
	}

	private static Display.BlockDisplay heldBlock(IronGolem golem) {
		for (Entity passenger : golem.getPassengers()) {
			if (passenger instanceof Display.BlockDisplay display) {
				return display;
			}
		}
		return null;
	}

	/** Stone floor under the whole 7x7 test area. */
	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 7; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	/** Spawns a player-built golem (never fights players back) at relative (x, 1, 2). */
	private static IronGolem spawnGolem(GameTestHelper helper, int x) {
		IronGolem golem = helper.spawn(EntityTypes.IRON_GOLEM, new BlockPos(x, 1, 2));
		golem.setPlayerCreated(true);
		return golem;
	}

	/** Creates a fresh fake player at a relative position inside the test area. */
	private static FakePlayer spawnPlayer(GameTestHelper helper, double x, double y, double z) {
		int n = PLAYER_COUNTER.incrementAndGet();
		FakePlayer player = FakePlayer.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "igt_test_" + n));
		player.snapTo(helper.absoluteVec(new Vec3(x, y, z)));
		player.setDeltaMovement(Vec3.ZERO);
		return player;
	}

	private static void hit(GameTestHelper helper, FakePlayer player, IronGolem golem, float damage) {
		golem.invulnerableTime = 0;
		golem.hurtServer(helper.getLevel(), player.damageSources().playerAttack(player), damage);
	}
}
