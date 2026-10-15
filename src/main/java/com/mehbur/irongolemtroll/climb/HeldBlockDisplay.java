package com.mehbur.irongolemtroll.climb;

import com.mehbur.irongolemtroll.IronGolemTroll;
import com.mojang.math.Transformation;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.level.block.Blocks;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The iron block shown in the golem's hand while it climbs.
 *
 * <p>The vanilla golem renderer only draws its poppy, never a held item, so the block
 * is a vanilla block display riding the golem. That needs nothing on the client.
 * Displays are tagged so leftovers (e.g. from a server stop mid-climb) can be found
 * and removed when they load again.
 */
final class HeldBlockDisplay {
	static final String TAG = IronGolemTroll.MOD_ID + ".held_block";

	private static final float SCALE = 0.45f;
	/** Center of the right hand relative to the golem's feet, in its facing frame (+Z forward, -X right). */
	private static final Vector3f HAND = new Vector3f(-0.85f, 0.55f, 0.15f);

	private HeldBlockDisplay() {
	}

	/** Spawns the block in the golem's hand, or returns {@code null} if that is not possible. */
	static Display.BlockDisplay spawn(IronGolem golem) {
		ServerLevel level = (ServerLevel) golem.level();
		Display.BlockDisplay display = EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
		if (display == null) {
			return null;
		}

		// Everything is set before spawning: the client gets the display's data once, when it
		// starts tracking it, and later changes to the transformation are not reliably applied.
		display.setBlockState(Blocks.IRON_BLOCK.defaultBlockState());
		// Riding puts the display at the golem's passenger point; shift the block back down to the hand.
		float riderOffset = (float) (golem.getPassengerRidingPosition(display).y - golem.getY());
		Vector3f corner = new Vector3f(HAND).sub(SCALE / 2, SCALE / 2 + riderOffset, SCALE / 2);
		display.setTransformation(new Transformation(corner, new Quaternionf(), new Vector3f(SCALE), new Quaternionf()));
		display.snapTo(golem.getX(), golem.getY() + riderOffset, golem.getZ(), golem.yBodyRot, 0.0f);

		if (!level.addFreshEntity(display)) {
			return null;
		}
		// Tagged only after spawning, so the load event of this fresh display does not flag it as a leftover.
		display.addTag(TAG);

		if (!display.startRiding(golem, true, true)) {
			display.discard();
			return null;
		}
		return display;
	}

	/** Keeps the block on the golem's right side when its body turns. */
	static void follow(Display.BlockDisplay display, IronGolem golem) {
		if (display != null && !display.isRemoved() && display.getYRot() != golem.yBodyRot) {
			display.setYRot(golem.yBodyRot);
		}
	}
}
