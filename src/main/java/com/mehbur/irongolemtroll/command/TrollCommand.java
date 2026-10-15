package com.mehbur.irongolemtroll.command;

import com.mehbur.irongolemtroll.IronGolemTroll;
import com.mehbur.irongolemtroll.config.TrollConfig;
import com.mehbur.irongolemtroll.loop.LoopControl;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * {@code /igtroll [on|off|toggle|reload|status]}, restricted to operators (permission level 2).
 *
 * <p>Messages use translation keys with English fallbacks, so players without the
 * mod installed still see readable text.
 */
public final class TrollCommand {
	private static final String KEY = IronGolemTroll.MOD_ID + ".";

	private TrollCommand() {
	}

	public static void register(LoopControl loops) {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> build(dispatcher, loops));
	}

	private static void build(CommandDispatcher<CommandSourceStack> dispatcher, LoopControl loops) {
		dispatcher.register(Commands.literal("igtroll")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(ctx -> status(ctx, loops))
				.then(Commands.literal("on").executes(ctx -> setEnabled(ctx, loops, true)))
				.then(Commands.literal("off").executes(ctx -> setEnabled(ctx, loops, false)))
				.then(Commands.literal("toggle").executes(ctx -> setEnabled(ctx, loops, !TrollConfig.get().enabled)))
				.then(Commands.literal("reload").executes(ctx -> reload(ctx, loops)))
				.then(Commands.literal("status").executes(ctx -> status(ctx, loops))));
	}

	private static int setEnabled(CommandContext<CommandSourceStack> ctx, LoopControl loops, boolean enabled) {
		TrollConfig.get().enabled = enabled;
		TrollConfig.save();
		if (!enabled) {
			loops.stopAll();
		}

		Component message = enabled
				? Component.translatableWithFallback(KEY + "command.enabled", "Iron Golem Troll enabled.")
				: Component.translatableWithFallback(KEY + "command.disabled", "Iron Golem Troll disabled. All active loops were stopped.");
		ctx.getSource().sendSuccess(() -> message, true);
		return 1;
	}

	private static int reload(CommandContext<CommandSourceStack> ctx, LoopControl loops) {
		if (!TrollConfig.load()) {
			ctx.getSource().sendFailure(Component.translatableWithFallback(KEY + "command.reload_failed",
					"Could not reload the config, see the server log."));
			return 0;
		}

		// The reloaded file may have switched the mod off.
		if (!TrollConfig.get().enabled) {
			loops.stopAll();
		}
		ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(KEY + "command.reloaded",
				"Iron Golem Troll config reloaded."), true);
		return 1;
	}

	private static int status(CommandContext<CommandSourceStack> ctx, LoopControl loops) {
		boolean enabled = TrollConfig.get().enabled;
		Component state = enabled
				? Component.translatableWithFallback(KEY + "state.on", "ON").withStyle(ChatFormatting.GREEN)
				: Component.translatableWithFallback(KEY + "state.off", "OFF").withStyle(ChatFormatting.RED);
		int active = loops.activeLoopCount();

		ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(KEY + "command.status",
				"Iron Golem Troll is %s. Active loops: %s", state, active), false);
		return active;
	}
}
