package com.github.akaretka.client.idle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.github.akaretka.InspectAnimationsIdle;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Minimal JSON config for the idle feature ({@code config/inspect-animations-idle.json}).
 *
 * <p>State is held in public static fields for zero-cost reads from the per-tick hot path.
 * Writing is atomic (temp file + {@code ATOMIC_MOVE}) so a crash mid-save cannot corrupt
 * the config; I/O failures are logged once and never propagate to the game loop.
 */
public final class IdleConfig {

	/** Bounds applied both on load and on save; the slider screen reuses them. */
	public static final int MIN_IDLE_SECONDS = 1;
	public static final int MAX_IDLE_SECONDS = 180;

	/** Master switch; flips before the counter check in the tick path */
	public static boolean enabled = true;
	/** Timeout in seconds, as shown in the UI and stored in the config file. */
	public static int idleSeconds = 16;
	/**
	 * Cached idle window in client ticks ({@link #idleSeconds} * 20), refreshed whenever
	 * {@link #idleSeconds} changes - the per-tick path only compares and never recomputes.
	 */
	public static int intervalTicks = 16 * 20;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Path path;
	private static boolean warnedIo;

	private IdleConfig() {
	}

	/** Reads the config file; a missing file is created with defaults on the spot. */
	public static void load() {
		path = FabricLoader.getInstance().getConfigDir().resolve("inspect-animations-idle.json");
		if (!Files.isRegularFile(path)) {
			save();
			return;
		}
		try {
			JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
			if (json.has("enabled")) enabled = json.get("enabled").getAsBoolean();
			if (json.has("idleSeconds")) apply(json.get("idleSeconds").getAsInt());
		} catch (Throwable t) {
			warnIo("config read failed, using defaults", t);
		}
		apply(idleSeconds);
	}

	/**
	 * Single point of entering a timeout: clamps it into the configured range and refreshes
	 * the cached tick window, so every reader agrees on one value.
	 *
	 * @param seconds new timeout in seconds (from the config file or the Mod Menu slider)
	 */
	public static void apply(int seconds) {
		idleSeconds = clamp(seconds);
		intervalTicks = idleSeconds * 20;
	}

	/** Writes via a temp file and an atomic move; safe to call concurrently on the client thread. */
	public static void save() {
		// Keep the invariant "stored value = sanitized value" even for hand-edited configs.
		apply(idleSeconds);
		try {
			JsonObject json = new JsonObject();
			json.addProperty("enabled", enabled);
			json.addProperty("idleSeconds", idleSeconds);
			Path file = path != null ? path : FabricLoader.getInstance().getConfigDir().resolve("inspect-animations-idle.json");
			Path temp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(temp, GSON.toJson(json));
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (Throwable t) {
			warnIo("config write failed", t);
		}
	}

	/** Keeps values sane even for hand-edited or version-migrated configs. */
	private static int clamp(int seconds) {
		return Math.clamp(seconds, MIN_IDLE_SECONDS, MAX_IDLE_SECONDS);
	}

	private static void warnIo(String message, Throwable cause) {
		if (warnedIo) return;
		warnedIo = true;
		InspectAnimationsIdle.LOGGER.error("[idle] config: {}", message, cause);
	}
}
