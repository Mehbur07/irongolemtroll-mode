package com.mehbur.irongolemtroll.climb;

import com.mehbur.irongolemtroll.physics.LaunchMath;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One troll run of a golem: it walks right up to the player who hit it, then pillars
 * up next to them with iron blocks the way a player "nerd-poles": hop, place a block
 * where the feet were, land on it, repeat.
 *
 * <p>Until the run is over the golem has no target, so it cannot attack; the manager
 * additionally cancels any damage it would deal.
 */
final class Climb {
	private enum Phase { APPROACH, READY, RISING, LANDING }

	/** Hop a bit more than one block so the new block fits under the feet with room to spare. */
	private static final double HOP_HEIGHT = 1.2;
	/** A climb step that takes longer than this (stuck, pushed into water, ...) ends the run. */
	private static final int STEP_TIMEOUT_TICKS = 40;
	/** Walking to the player gives up after this long and the golem climbs where it is. */
	private static final int APPROACH_TIMEOUT_TICKS = 200;
	/** Close enough to the spot next to the player to start climbing. */
	private static final double ARRIVED_DISTANCE = 0.3;
	/** Within this distance the path finder is dropped and the golem slides the rest of the way. */
	private static final double SLIDE_DISTANCE = 2.0;
	private static final double SLIDE_SPEED = 0.15;
	/** Ticks without getting closer before the slide counts as blocked. */
	private static final int STUCK_TICKS = 10;
	/** Gap between the golem and the face of the player's column, against float rounding. */
	private static final double FACE_GAP = 0.01;

	final IronGolem golem;
	private final ServerPlayer player;
	private final Display.BlockDisplay heldBlock;

	private int blocksLeft;
	private Phase phase = Phase.APPROACH;
	private int phaseStartTick;
	private Vec3 spot;
	private double bestDistance = Double.MAX_VALUE;
	private int lastProgressTick;
	private double lockX;
	private double lockZ;
	private BlockPos column;
	private LivingEntity savedTarget;

	Climb(IronGolem golem, ServerPlayer player, int blocks, int now) {
		this.golem = golem;
		this.player = player;
		this.blocksLeft = blocks;
		this.phaseStartTick = now;
		this.lastProgressTick = now;
		this.spot = spotNextTo(player);
		this.heldBlock = HeldBlockDisplay.spawn(golem);
	}

	Display.BlockDisplay heldBlock() {
		return heldBlock;
	}

	/** True while the golem is still walking to the player. */
	boolean isApproaching() {
		return phase == Phase.APPROACH;
	}

	/** Advances the run by one server tick. @return {@code true} once it is over */
	boolean tick(int now) {
		keepOutOfFights();
		HeldBlockDisplay.follow(heldBlock, golem);

		if (phase == Phase.APPROACH) {
			approach(now);
			return false;
		}

		lockOnColumn();
		if (now - phaseStartTick > STEP_TIMEOUT_TICKS) {
			return true;
		}

		ServerLevel level = (ServerLevel) golem.level();
		switch (phase) {
			case READY -> {
				if (golem.onGround()) {
					if (!hop(level)) {
						return true; // no room for another block
					}
					enter(Phase.RISING, now);
				}
			}
			case RISING -> {
				if (golem.getY() >= column.getY() + 1.0) {
					if (!level.getBlockState(column).canBeReplaced()) {
						return true; // something filled the spot meanwhile
					}
					placeIronBlock(level);
					blocksLeft--;
					enter(Phase.LANDING, now);
				} else if (golem.getDeltaMovement().y <= 0.0) {
					return true; // hop was cut short, e.g. by a ceiling
				}
			}
			case LANDING -> {
				if (golem.onGround()) {
					if (blocksLeft <= 0) {
						return true;
					}
					enter(Phase.READY, now);
				}
			}
			default -> {
			}
		}
		return false;
	}

	/** Ends the run: gives the golem its target back so it may attack again. */
	void finish() {
		golem.getNavigation().stop();
		if (golem.isAlive() && !golem.isRemoved() && savedTarget != null && savedTarget.isAlive()
				&& !savedTarget.isRemoved() && savedTarget.level() == golem.level()) {
			golem.setTarget(savedTarget);
		}
		savedTarget = null;
	}

	/** Walks to the spot next to the player, then switches to climbing. */
	private void approach(int now) {
		if (player.isAlive() && !player.isRemoved() && player.level() == golem.level()) {
			spot = spotNextTo(player); // follow the player while they move
		}

		double distance = horizontalDistance(spot);
		if (distance < bestDistance - 0.05) {
			bestDistance = distance;
			lastProgressTick = now;
		}

		boolean arrived = distance < ARRIVED_DISTANCE;
		boolean stuck = distance < SLIDE_DISTANCE && now - lastProgressTick > STUCK_TICKS;
		boolean gaveUp = now - phaseStartTick > APPROACH_TIMEOUT_TICKS;
		if (arrived || stuck || gaveUp) {
			startClimbing(now);
			return;
		}

		if (distance < SLIDE_DISTANCE) {
			// The path finder treats the 1.4 wide golem as 2 blocks wide and stops short of
			// the player's column, so close in on the exact spot by hand.
			golem.getNavigation().stop();
			double step = Math.min(SLIDE_SPEED, distance);
			double vx = (spot.x - golem.getX()) / distance * step;
			double vz = (spot.z - golem.getZ()) / distance * step;
			golem.setDeltaMovement(vx, golem.getDeltaMovement().y, vz);
			golem.getLookControl().setLookAt(player);
		} else if (golem.getNavigation().isDone() || (now - phaseStartTick) % 10 == 0) {
			golem.getNavigation().moveTo(spot.x, golem.getY(), spot.z, 1.0);
		}
	}

	private void startClimbing(int now) {
		golem.getNavigation().stop();
		// Lock on the spot next to the player if the golem fits there, else on its own block.
		if (fitsAt(spot.x, spot.z)) {
			lockX = spot.x;
			lockZ = spot.z;
		} else {
			BlockPos here = golem.blockPosition();
			lockX = here.getX() + 0.5;
			lockZ = here.getZ() + 0.5;
			if (!fitsAt(lockX, lockZ)) {
				lockX = golem.getX();
				lockZ = golem.getZ();
			}
		}
		enter(Phase.READY, now);
	}

	/** Starts one hop if there is room. @return {@code false} if the golem cannot go higher */
	private boolean hop(ServerLevel level) {
		column = BlockPos.containing(lockX, golem.getY(), lockZ);
		if (!level.getBlockState(column).canBeReplaced()) {
			return false;
		}
		if (!level.noCollision(golem, golem.getBoundingBox().move(0.0, HOP_HEIGHT, 0.0))) {
			return false;
		}

		double vy = LaunchMath.velocityForHeight(HOP_HEIGHT, golem.getGravity());
		golem.setDeltaMovement(0.0, vy, 0.0);
		golem.hurtMarked = true; // send the new velocity to clients right away
		return true;
	}

	private void placeIronBlock(ServerLevel level) {
		BlockState iron = Blocks.IRON_BLOCK.defaultBlockState();
		level.setBlockAndUpdate(column, iron);
		SoundType sound = iron.getSoundType();
		level.playSound(null, column, sound.getPlaceSound(), SoundSource.BLOCKS,
				(sound.getVolume() + 1.0f) / 2.0f, sound.getPitch() * 0.8f);
	}

	/** No target means no attacks; the old target is handed back in {@link #finish()}. */
	private void keepOutOfFights() {
		LivingEntity target = golem.getTarget();
		if (target != null) {
			savedTarget = target;
			golem.setTarget(null);
		}
	}

	/** Keeps the golem standing still on its column and facing the player while it climbs. */
	private void lockOnColumn() {
		golem.getNavigation().stop();
		Vec3 motion = golem.getDeltaMovement();
		if (motion.x != 0.0 || motion.z != 0.0) {
			golem.setDeltaMovement(0.0, motion.y, 0.0);
		}
		if (golem.getX() != lockX || golem.getZ() != lockZ) {
			golem.setPos(lockX, golem.getY(), lockZ);
		}
		if (player.isAlive() && !player.isRemoved()) {
			golem.getLookControl().setLookAt(player);
		}
	}

	/**
	 * The spot where the golem stands flush against the player's block column, on the side
	 * it comes from. Grid aligned, so the golem's pillar ends up right next to the player's.
	 */
	private Vec3 spotNextTo(ServerPlayer target) {
		BlockPos cell = target.blockPosition();
		double halfWidth = golem.getBbWidth() / 2.0;
		double dx = golem.getX() - (cell.getX() + 0.5);
		double dz = golem.getZ() - (cell.getZ() + 0.5);

		if (Math.abs(dx) >= Math.abs(dz)) {
			double x = dx >= 0 ? cell.getX() + 1 + halfWidth + FACE_GAP : cell.getX() - halfWidth - FACE_GAP;
			return new Vec3(x, golem.getY(), cell.getZ() + 0.5);
		}
		double z = dz >= 0 ? cell.getZ() + 1 + halfWidth + FACE_GAP : cell.getZ() - halfWidth - FACE_GAP;
		return new Vec3(cell.getX() + 0.5, golem.getY(), z);
	}

	private boolean fitsAt(double x, double z) {
		AABB box = golem.getBoundingBox().move(x - golem.getX(), 0.0, z - golem.getZ());
		return golem.level().noCollision(golem, box);
	}

	private double horizontalDistance(Vec3 target) {
		return Mth.length(target.x - golem.getX(), target.z - golem.getZ());
	}

	private void enter(Phase next, int now) {
		phase = next;
		phaseStartTick = now;
	}
}
