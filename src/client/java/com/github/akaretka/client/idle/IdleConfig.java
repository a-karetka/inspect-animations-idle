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
 *
 * <p>Every knob has a {@code DEFAULT_*} constant: the config screen offers a per-parameter
 * "reset to default", and a missing key in a hand-edited or version-migrated file falls back
 * to the same constant, so file defaults and UI defaults can never drift apart.
 */
public final class IdleConfig {

	/** Bounds applied both on load and on save; the slider screen reuses them. */
	public static final int MIN_IDLE_SECONDS = 1;
	public static final int MAX_IDLE_SECONDS = 120;
	/**
	 * Bounds and default for the head-turn trigger threshold (degrees of yaw change per tick).
	 * 1 keeps the trigger usable as "any camera move"; 90 keeps it distinguishable from flicks
	 * a user may not consider activity.
	 */
	public static final int MIN_HEAD_TURN_DEGREES = 1;
	public static final int MAX_HEAD_TURN_DEGREES = 90;
	public static final int DEFAULT_HEAD_TURN_DEGREES = 15;
	/**
	 * State-trigger checkpoint period in client ticks (power of two, masked against the
	 * already-incremented idle counter). Moving/sneaking/flying states shorter than this may
	 * miss a reset - accepted as a deliberate sampling trade-off: it keeps every disabled
	 * trigger at a fixed one-flag-read cost per tick instead of six full condition checks.
	 */
	public static final int STATE_CHECK_MASK = 3;

	/** Master switch; flips before the counter check in the tick path */
	public static boolean enabled = true;
	/**
	 * Defaults for the two windows: the first window is longer (20 s) because idle starts
	 * whenever the player does anything at all, while a fired auto-trigger already proves
	 * the player has moved away from the keyboard (10 s). Split in 1.1.0; 1.0.x had a single 16 s.
	 */
	public static final int DEFAULT_FIRST_SECONDS = 20;
	public static final int DEFAULT_NEXT_SECONDS = 10;
	/** First timeout after any activity reset, as shown in the UI and stored in the config file. */
	public static int firstSeconds = DEFAULT_FIRST_SECONDS;
	/** Timeout for every subsequent auto-trigger while no new activity happens. */
	public static int nextSeconds = DEFAULT_NEXT_SECONDS;
	/**
	 * Cached idle windows in client ticks, refreshed whenever the seconds values change - the
	 * per-tick path only compares and never recomputes. {@code windowTicks} holds the window
	 * currently charged: the first one after any activity, the subsequent one after trigger.
	 */
	public static int firstIntervalTicks = DEFAULT_FIRST_SECONDS * 20;
	public static int nextIntervalTicks = DEFAULT_NEXT_SECONDS * 20;
	/** Threshold in degrees for the head-turn trigger, clamped on change. */
	public static int headTurnDegrees = DEFAULT_HEAD_TURN_DEGREES;

	// ── Per-trigger switches: every reset source can be turned off individually.
	// Action sources fire from mixins / the offhand key check; state sources are sampled
	// in the tick path every {@link #STATE_CHECK_MASK} ticks. All six action triggers
	// default to true in 1.1.0 - they only fire on real player activity; the state
	// triggers default to false to keep 1.0.x behaviour.

	/** Item switch via hotbar keys 1-9 / mouse wheel / programmatic selection. */
	public static boolean resetSlotSwitch = true;
	/** Right-click item use (startUseItem; re-fires every tick the key is held). */
	public static boolean resetItemUse = true;
	/** Left-click attack press. */
	public static boolean resetAttack = true;
	/** Held left click (block breaking / repeated attacks). */
	public static boolean resetAttackHold = true;
	/** Offhand swap key (F): switches the held item but never passes through the inventory. */
	public static boolean resetOffhandSwap = true;
	/** Any change of the item stack in the selected hotbar slot or the offhand slot. */
	public static boolean resetItemReplace = true;
	/** Sprinting state (implies movement; sampled per checkpoint). */
	public static boolean resetSprint = false;
	/** Voluntary horizontal movement (walk / strafe; sampled per checkpoint). */
	public static boolean resetWalk = false;
	/** Sneaking / crouching state (sampled per checkpoint). */
	public static boolean resetCrouch = false;
	/** Camera yaw changed by more than {@link #headTurnDegrees} since the previous tick. */
	public static boolean resetHeadTurn = false;
	/** Creative/spectator flight state (sampled per checkpoint). */
	public static boolean resetCreativeFly = false;
	/** Elytra gliding state (sampled per checkpoint). */
	public static boolean resetElytraFly = false;

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
			// "idleSeconds" is the 1.0.x key; keep it as the first window so old configs
			// migrate without a visible behaviour change. "nextSeconds" was never written
			// before 1.1.0, so its absence means "equal to the first window".
			if (json.has("firstSeconds")) applyFirst(json.get("firstSeconds").getAsInt());
			else if (json.has("idleSeconds")) applyFirst(json.get("idleSeconds").getAsInt());
			if (json.has("nextSeconds")) applyNext(json.get("nextSeconds").getAsInt());
			else applyNext(firstSeconds);
			if (json.has("headTurnDegrees")) {
				headTurnDegrees = Math.clamp(json.get("headTurnDegrees").getAsInt(), MIN_HEAD_TURN_DEGREES, MAX_HEAD_TURN_DEGREES);
			}
			// Every trigger reads its own switch; absent keys mean "off" for both new and
			// migrated files. Booleans cannot fail parsing themselves, so a malformed file
			// fails fully into the catch below - same as before.
			resetSlotSwitch = optBool(json, "resetSlotSwitch");
			resetItemUse = optBool(json, "resetItemUse");
			resetAttack = optBool(json, "resetAttack");
			resetAttackHold = optBool(json, "resetAttackHold");
			resetOffhandSwap = optBool(json, "resetOffhandSwap");
			resetItemReplace = optBool(json, "resetItemReplace");
			resetSprint = optBool(json, "resetSprint");
			resetWalk = optBool(json, "resetWalk");
			resetCrouch = optBool(json, "resetCrouch");
			resetHeadTurn = optBool(json, "resetHeadTurn");
			resetCreativeFly = optBool(json, "resetCreativeFly");
			resetElytraFly = optBool(json, "resetElytraFly");
		} catch (Throwable t) {
			warnIo("config read failed, using defaults", t);
		}
		applyFirst(firstSeconds);
		applyNext(nextSeconds);
	}

	/**
	 * Single point of entering a timeout: clamps it into the configured range and refreshes
	 * the cached first-window tick count, so every reader agrees on one value.
	 *
	 * @param seconds new first-window timeout in seconds (config file UI slider)
	 */
	public static void applyFirst(int seconds) {
		firstSeconds = clamp(seconds);
		firstIntervalTicks = firstSeconds * 20;
		IdleInspector.onFirstWindowConfigChanged(firstIntervalTicks);
	}

	/**
	 * Same contract as {@link #applyFirst(int)} for the subsequent-trigger window.
	 *
	 * @param seconds new subsequent-window timeout in seconds
	 */
	public static void applyNext(int seconds) {
		nextSeconds = clamp(seconds);
		nextIntervalTicks = nextSeconds * 20;
		IdleInspector.onNextWindowConfigChanged(nextIntervalTicks);
	}

	/** Clamps the head-turn threshold into its configured range; used by load and the screen. */
	public static void applyHeadTurn(int degrees) {
		headTurnDegrees = Math.clamp(degrees, MIN_HEAD_TURN_DEGREES, MAX_HEAD_TURN_DEGREES);
	}

	/** Writes via a temp file and an atomic move; safe to call concurrently on the client thread. */
	public static void save() {
		// Keep the invariant "stored value = sanitized value" even for hand-edited configs.
		applyFirst(firstSeconds);
		applyNext(nextSeconds);
		applyHeadTurn(headTurnDegrees);
		try {
			JsonObject json = new JsonObject();
			json.addProperty("enabled", enabled);
			json.addProperty("firstSeconds", firstSeconds);
			json.addProperty("nextSeconds", nextSeconds);
			json.addProperty("headTurnDegrees", headTurnDegrees);
			json.addProperty("resetSlotSwitch", resetSlotSwitch);
			json.addProperty("resetItemUse", resetItemUse);
			json.addProperty("resetAttack", resetAttack);
			json.addProperty("resetAttackHold", resetAttackHold);
			json.addProperty("resetOffhandSwap", resetOffhandSwap);
			json.addProperty("resetItemReplace", resetItemReplace);
			json.addProperty("resetSprint", resetSprint);
			json.addProperty("resetWalk", resetWalk);
			json.addProperty("resetCrouch", resetCrouch);
			json.addProperty("resetHeadTurn", resetHeadTurn);
			json.addProperty("resetCreativeFly", resetCreativeFly);
			json.addProperty("resetElytraFly", resetElytraFly);
			Path file = path != null ? path : FabricLoader.getInstance().getConfigDir().resolve("inspect-animations-idle.json");
			Path temp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(temp, GSON.toJson(json));
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (Throwable t) {
			warnIo("config write failed", t);
		}
	}

	/** Reads a boolean key that may be absent (migrated 1.0.x file); missing means "off". */
	private static boolean optBool(JsonObject json, String key) {
		return json.has(key) && json.get(key).getAsBoolean();
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
