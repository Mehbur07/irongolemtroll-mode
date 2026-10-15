package com.mehbur.irongolemtroll.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.mehbur.irongolemtroll.IronGolemTroll;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JSON backed configuration stored at {@code config/iron_golem_troll.json}.
 *
 * <p>Field names are the JSON keys, so renaming a field breaks existing files.
 * Missing keys fall back to the defaults below, and out-of-range values are
 * clamped by {@link #validate()}.
 */
public final class TrollConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve(IronGolemTroll.MOD_ID + ".json");

	private static TrollConfig instance = new TrollConfig();

	/** Master switch, also toggled by {@code /igtroll on|off|toggle}. */
	public boolean enabled = true;
	/** Blocks the player must rise above the hit height, and blocks the golem is launched. */
	public double riseHeight = 3.0;
	/** Ticks after a hit during which the player reaching the target height triggers the golem. */
	public int triggerWindowTicks = 40;
	/** Loop stops when the player is farther than this from the golem. */
	public double maxDistance = 10.0;
	/** Loop stops when the player stands on the ground this long without hitting the golem. */
	public int cooldownResetSeconds = 5;
	/** If true the golem throws the player up by {@link #riseHeight} on every hit, which starts the loop. */
	public boolean golemLaunchesPlayer = true;

	public static TrollConfig get() {
		return instance;
	}

	/**
	 * Loads the config from disk, creating it with defaults if it does not exist.
	 * On a read or parse error the current values are kept and the broken file is
	 * left untouched so the user can fix it.
	 *
	 * @return {@code true} if the file was read (or created) successfully
	 */
	public static boolean load() {
		if (Files.notExists(PATH)) {
			instance = new TrollConfig();
			save();
			IronGolemTroll.LOGGER.info("Created default config at {}", PATH);
			return true;
		}

		try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
			TrollConfig loaded = GSON.fromJson(reader, TrollConfig.class);
			if (loaded == null) {
				throw new JsonParseException("config file is empty");
			}
			loaded.validate();
			instance = loaded;
			// Rewrite so newly added keys and clamped values show up in the file.
			save();
			return true;
		} catch (IOException | JsonParseException e) {
			IronGolemTroll.LOGGER.warn("Could not read {}, keeping current values: {}", PATH, e.getMessage());
			return false;
		}
	}

	/** Writes the current values to disk. */
	public static void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			IronGolemTroll.LOGGER.error("Could not write {}", PATH, e);
		}
	}

	/** Clamps every value into a range the loop logic can safely use. */
	private void validate() {
		riseHeight = clamp(riseHeight, 0.5, 64.0, 3.0);
		triggerWindowTicks = (int) clamp(triggerWindowTicks, 1, 1200, 40);
		maxDistance = clamp(maxDistance, 1.0, 128.0, 10.0);
		cooldownResetSeconds = (int) clamp(cooldownResetSeconds, 1, 600, 5);
	}

	private static double clamp(double value, double min, double max, double fallback) {
		if (Double.isNaN(value) || Double.isInfinite(value)) {
			return fallback;
		}
		return Math.max(min, Math.min(max, value));
	}
}
