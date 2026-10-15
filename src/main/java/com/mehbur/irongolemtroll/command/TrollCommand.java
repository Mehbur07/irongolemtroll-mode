package com.mehbur.irongolemtroll.command;

import com.mehbur.irongolemtroll.IronGolemTroll;
import com.mehbur.irongolemtroll.climb.TrollControl;
import com.mehbur.irongolemtroll.config.TrollConfig;
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

	public static void register(TrollControl troll) {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> build(dispatcher, troll));
	}

	private static void build(CommandDispatcher<CommandSourceStack> dispatcher, TrollControl troll) {
		dispatcher.register(Commands.literal("igtroll")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(ctx -> status(ctx, troll))
				.then(Commands.literal("on").executes(ctx -> setEnabled(ctx, troll, true)))
				.then(Commands.literal("off").executes(ctx -> setEnabled(ctx, troll, false)))
				.then(Commands.literal("toggle").executes(ctx -> setEnabled(ctx, troll, !TrollConfig.get().enabled)))
				.then(Commands.literal("reload").executes(ctx -> reload(ctx, troll)))
				.then(Commands.literal("status").executes(ctx -> status(ctx, troll))));
	}

	private static int setEnabled(CommandContext<CommandSourceStack> ctx, TrollControl troll, boolean enabled) {
		TrollConfig.get().enabled = enabled;
		TrollConfig.save();
		if (!enabled) {
			troll.stopAll();
		}

		Component message = enabled
				? Component.translatableWithFallback(KEY + "command.enabled", "Iron Golem Troll enabled.")
				: Component.translatableWithFallback(KEY + "command.disabled", "Iron Golem Troll disabled. All climbs were stopped.");
		ctx.getSource().sendSuccess(() -> message, true);
		return 1;
	}

	private static int reload(CommandContext<CommandSourceStack> ctx, TrollControl troll) {
		if (!TrollConfig.load()) {
			ctx.getSource().sendFailure(Component.translatableWithFallback(KEY + "command.reload_failed",
					"Could not reload the config, see the server log."));
			return 0;
		}

		// The reloaded file may have switched the mod off.
		if (!TrollConfig.get().enabled) {
			troll.stopAll();
		}
		ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(KEY + "command.reloaded",
				"Iron Golem Troll config reloaded."), true);
		return 1;
	}

	private static int status(CommandContext<CommandSourceStack> ctx, TrollControl troll) {
		boolean enabled = TrollConfig.get().enabled;
		Component state = enabled
				? Component.translatableWithFallback(KEY + "state.on", "ON").withStyle(ChatFormatting.GREEN)
				: Component.translatableWithFallback(KEY + "state.off", "OFF").withStyle(ChatFormatting.RED);
		int active = troll.activeCount();

		ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(KEY + "command.status",
				"Iron Golem Troll is %s. Climbing golems: %s", state, active), false);
		return active;
	}
}
