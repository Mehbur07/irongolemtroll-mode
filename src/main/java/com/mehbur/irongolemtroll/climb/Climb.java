package com.mehbur.irongolemtroll.climb;

import com.mehbur.irongolemtroll.physics.LaunchMath;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * One golem pillaring up with iron blocks, the way a player "nerd-poles":
 * hop, place a block where the feet were, land on it, repeat.
 *
 * <p>While climbing the golem stands locked on its column and has no target, so it
 * cannot attack; the manager additionally cancels any damage it would deal.
 */
final class Climb {
	private enum Phase { READY, RISING, LANDING }

	/** Hop a bit more than one block so the new block fits under the feet with room to spare. */
	private static final double HOP_HEIGHT = 1.2;
	/** A step that takes longer than this (stuck, pushed into water, ...) ends the climb. */
	private static final int STEP_TIMEOUT_TICKS = 40;

	final IronGolem golem;
	private final double lockX;
	private final double lockZ;
	private final Display.BlockDisplay heldBlock;

	private int blocksLeft;
	private Phase phase = Phase.READY;
	private int phaseStartTick;
	private BlockPos column;
	private LivingEntity savedTarget;

	Climb(IronGolem golem, int blocks, int now) {
		this.golem = golem;
		BlockPos start = golem.blockPosition();
		this.lockX = start.getX() + 0.5;
		this.lockZ = start.getZ() + 0.5;
		this.blocksLeft = blocks;
		this.phaseStartTick = now;
		this.heldBlock = HeldBlockDisplay.spawn(golem);
	}

	Display.BlockDisplay heldBlock() {
		return heldBlock;
	}

	/** Advances the climb by one server tick. @return {@code true} once the climb is over */
	boolean tick(int now) {
		holdStill();
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
		}
		return false;
	}

	/** Ends the climb: gives the golem its target back so it may attack again. */
	void finish() {
		if (golem.isAlive() && !golem.isRemoved() && savedTarget != null && savedTarget.isAlive() && !savedTarget.isRemoved()
				&& savedTarget.level() == golem.level()) {
			golem.setTarget(savedTarget);
		}
		savedTarget = null;
	}

	/** Starts one hop if there is room. @return {@code false} if the golem cannot go higher */
	private boolean hop(ServerLevel level) {
		column = golem.blockPosition();
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

	/** Keeps the golem on its column and out of fights while it climbs. */
	private void holdStill() {
		golem.getNavigation().stop();
		LivingEntity target = golem.getTarget();
		if (target != null) {
			savedTarget = target;
			golem.setTarget(null);
		}

		Vec3 motion = golem.getDeltaMovement();
		if (motion.x != 0.0 || motion.z != 0.0) {
			golem.setDeltaMovement(0.0, motion.y, 0.0);
		}
		if (golem.getX() != lockX || golem.getZ() != lockZ) {
			golem.setPos(lockX, golem.getY(), lockZ);
		}
		HeldBlockDisplay.follow(heldBlock, golem);
	}

	private void enter(Phase next, int now) {
		phase = next;
		phaseStartTick = now;
	}
}
