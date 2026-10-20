package com.github.akaretka.client.idle;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.ThreadLocalRandom;

import com.github.akaretka.InspectAnimationsIdle;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Triggers one of the Inspect Animations actions after the configured idle timeout.
 *
 * <p>The integration is a runtime probe instead of a direct dependency: Inspect Animations
 * does not expose a public API, so its package-private {@code AnimationHelper.sendAnimation}
 * methods are resolved once via reflection and re-played exactly like a key press would.
 * The probe runs once at client startup ({@link #init()}), before the first tick, so an
 * incompatible-or-missing upstream is reported in the log pre-game instead of after the
 * first idle timeout; the trigger path never pays for reflection.
 * If Inspect Animations is missing or incompatible, the feature disables itself instead of crashing.
 */
public final class IdleInspector {

	/** Mod id of Inspect Animations (without dash, as declared in its fabric.mod.json). */
	private static final String INSPECT_MOD_ID = "inspectanimations";
	/** Integration target; resolved through reflection because the class is private to the other mod. */
	private static final String ANIMATION_HELPER_CLASS = "net.soundsofthesun.inspectanimations.client.AnimationHelper";
	private static final String TURN_ANIMATION_NAME = "TURN";
	/**
	 * Supported Inspect Animations window, mirroring the {@code ^0.2.5} range in fabric.mod.json:
	 * 0.2.5 and any later 0.2.x patch; other majors/minors are rejected before the probe runs.
	 */
	private static final int SUPPORTED_MAJOR = 0;
	private static final int SUPPORTED_MINOR = 2;
	private static final int SUPPORTED_MIN_PATCH = 5;
	private static final String SUPPORTED_VERSION_RANGE = ">=0.2.5, <0.3.0";

	/**
	 * Armory slot indices in the player inventory container: the hotbar slots that funnel through
	 * {@code Inventory#setSelectedSlot} are 0-8 and the offhand container slot is 40 (the slots above
	 * belong to armor and must not count as "item in hand changed" activity).
	 */
	private static final int OFFHAND_SLOT = 40;

	/** Idle counter in client ticks; the timer restarts after every auto-trigger. */
	private static int idleTicks;
	/**
	 * Window currently charged, in ticks: always one of the two precomputed config windows.
	 * Kept as a plain field copied on mode switch - the per-tick compare must not pay for
	 * any "which window applies" branching. Initialized from the config default so the very
	 * first tick after class load compares against the first window, not zero.
	 */
	private static int windowTicks = IdleConfig.firstIntervalTicks;
	/** Which of the two windows is charged; flips on trigger and activity resets. */
	private static boolean firstWindowCharged = true;
	/** Fail-safe latch: any integration failure switches the feature off permanently. */
	private static boolean disabled;
	/** {@code AnimationHelper.sendAnimation(LocalPlayer)} - plays the currently selected animation. */
	private static Method sendSelectedAnimation;
	/** {@code AnimationHelper.sendAnimation(LocalPlayer, Animation)} - plays a concrete animation. */
	private static Method sendTurnAnimation;
	/** Cached {@code Animation.TURN} enum constant passed to {@link #sendTurnAnimation}. */
	private static Object turnAnimation;

	private IdleInspector() {
	}

	/**
	 * Runs the one-shot reflection probe; called once from the client entrypoint. The per-tick
	 * entry point ({@link #onClientTick(Minecraft)}) is driven by the vanilla hook in
	 * {@code MinecraftMixin}, there is nothing left to register here beyond resolving the
	 * upstream methods so the log gets its diagnosis at game start, not after the first window.
	 */
	public static void init() {
		resolveActions();
	}

	/**
	 * Arms the timeout for a fresh activity streak: counter to zero and the *first* window
	 * charged, per the agreed semantics "any activity restarts the initial timeout; only an
	 * actually fired auto-trigger charges the subsequent window".
	 */
	private static void armFirstWindow() {
		idleTicks = 0;
		windowTicks = IdleConfig.firstIntervalTicks;
		firstWindowCharged = true;
	}

	/**
	 * Live config edits must reach the currently charged window without an activity reset:
	 * before 1.1.0 the tick path read the config field directly, so a mid-streak slider change
	 * applied immediately. Keeping the charged copy without this hook would freeze the pending
	 * timeout at its old value until the next reset, a needless behaviour regression.
	 */
	public static void onFirstWindowConfigChanged(int newFirstTicks) {
		if (firstWindowCharged) windowTicks = newFirstTicks;
	}

	/** Same contract as {@link #onFirstWindowConfigChanged(int)} for the subsequent window. */
	public static void onNextWindowConfigChanged(int newNextTicks) {
		if (!firstWindowCharged) windowTicks = newNextTicks;
	}

	// ── Activity entry points. Each is one config-flag read plus a reset at most, which is
	// what keeps the mixins effectively free; sources are split into named methods so each
	// mixin reads only the flag of the trigger that calls it.

	/** Slot switch: hotbar keys 1-9, mouse wheel, programmatic selection ({@code Inventory#setSelectedSlot}). */
	public static void onSlotSwitch() {
		if (IdleConfig.resetSlotSwitch) armFirstWindow();
	}

	/** Right-click item use ({@code Minecraft#startUseItem}, re-invoked every held tick). */
	public static void onItemUse() {
		if (IdleConfig.resetItemUse) armFirstWindow();
	}

	/** Left-click attack press ({@code Minecraft#startAttack}). */
	public static void onAttack() {
		if (IdleConfig.resetAttack) armFirstWindow();
	}

	/** Held left click / block breaking ({@code Minecraft#continueAttack}). */
	public static void onAttackHold() {
		if (IdleConfig.resetAttackHold) armFirstWindow();
	}

	/**
	 * Item replaced: the stack in the selected hotbar slot or the offhand slot changed
	 * (pickup, placement, drop, any inventory click). Called from {@code Inventory#setItem},
	 * so the caller has already done the slot filtering.
	 */
	public static void onItemReplace() {
		if (IdleConfig.resetItemReplace) armFirstWindow();
	}

	/**
	 * Per-tick entry point, invoked from the vanilla {@code Minecraft#tick} tail (MinecraftMixin)
	 * - the same placement {@code ClientTickEvents.END_CLIENT_TICK} used before the Fabric API
	 * dependency was dropped.
	 *
	 * <p>Cheapest possible tick path: static flag read, counter increment, comparisons.
	 * Disabling resets the counter so that re-enabling starts a fresh window.
	 *
	 * <p>Invariant note (1.1.0): the per-tick budget was deliberately widened. Each state
	 * trigger below is its own config flag so a disabled trigger costs exactly one
	 * boolean field read; enabled state triggers are sampled once every
	 * IdleConfig.STATE_CHECK_MASK ticks via the already-incremented counter, which caps
	 * their worst-case cost at (checks / 4) primitive compares per tick. No allocations
	 * remain - the head-turn check uses the vanilla yRot/yRotO tick pair, no extra
	 * per-tick state is cached.
	 */
	public static void onClientTick(Minecraft minecraft) {
		if (!IdleConfig.enabled) {
			idleTicks = 0;
			windowTicks = IdleConfig.firstIntervalTicks;
			firstWindowCharged = true;
			return;
		}
		// Offhand swap (F) also switches the held item but never passes through
		// Inventory#setSelectedSlot, which the mixins already cover.
		// A key press leaves the mapping "down" only while the game is in-game (no screen open),
		// so this produces no false activity from GUIs or the title screen.
		if (IdleConfig.resetOffhandSwap && minecraft.options.keySwapOffhand.isDown()) armFirstWindow();
		if (++idleTicks < windowTicks) {
			// State triggers (movement, camera, flights) are conditions, not events - the
			// only way to react cheaply is sampling. Gated behind their flags and periodic,
			// so a fully disabled config pays nothing beyond the mask test.
			if ((idleTicks & IdleConfig.STATE_CHECK_MASK) == 0) checkStateTriggers(minecraft);
			return;
		}
		// Reset first so a failing invocation below can never cause a cascade of triggers.
		armFirstWindow();
		// Triggers are skipped in menus and while the game is paused; the counter itself
		// keeps running intentionally, per the agreed behaviour. The probe already ran at
		// client startup, so the disabled latch is the only gate left on this path.
		if (minecraft.player == null || minecraft.level == null || minecraft.isPaused()) return;
		if (disabled) return;
		Method method = pickAction();
		if (method == null) return;
		// Only an actually fired trigger charges the subsequent window.
		windowTicks = IdleConfig.nextIntervalTicks;
		firstWindowCharged = false;
		try {
			if (method == sendTurnAnimation) method.invoke(null, minecraft.player, turnAnimation);
			else method.invoke(null, minecraft.player);
		} catch (Throwable t) {
			disable("Inspect Animations invocation failed", t);
		}
	}

	/**
	 * State-trigger sampling, executed once every {@code STATE_CHECK_MASK + 1} ticks.
	 *
	 * <p>Ordered from cheapest to most expensive, guarded by the trigger flags, and bailed
	 * out before any virtual call while no player exists (title screen, disconnects, world
	 * transitions). Uses only vanilla field reads: input keystroke record, abilities flag,
	 * fall-flying check and the yaw tick pair the engine already maintains.
	 */
	private static void checkStateTriggers(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) return;
		if (IdleConfig.resetSprint && player.isSprinting()) {
			armFirstWindow();
			return;
		}
		if (IdleConfig.resetWalk && (player.input.keyPresses.forward() || player.input.keyPresses.backward()
				|| player.input.keyPresses.left() || player.input.keyPresses.right())) {
			armFirstWindow();
			return;
		}
		if (IdleConfig.resetCrouch && player.isCrouching()) {
			armFirstWindow();
			return;
		}
		if (IdleConfig.resetHeadTurn) {
			float turn = player.getYRot() - player.yRotO;
			if (turn < 0.0F) turn = -turn;
			if (turn >= IdleConfig.headTurnDegrees) {
				armFirstWindow();
				return;
			}
		}
		if (IdleConfig.resetCreativeFly && player.getAbilities().flying) {
			armFirstWindow();
			return;
		}
		if (IdleConfig.resetElytraFly && player.isFallFlying()) armFirstWindow();
	}

	/** Pure random pick among the available actions; falls back to whichever one exists. */
	private static Method pickAction() {
		if (sendSelectedAnimation == null) return sendTurnAnimation;
		if (sendTurnAnimation == null) return sendSelectedAnimation;
		return ThreadLocalRandom.current().nextBoolean() ? sendSelectedAnimation : sendTurnAnimation;
	}

	/**
	 * One-shot reflection probe.
	 *
	 * <p>Methods are selected by signature shape ({@code static void sendAnimation(LocalPlayer)} and
	 * {@code static void sendAnimation(LocalPlayer, Animation)}) rather than by name, so unrelated
	 * renames in Inspect Animations do not break the lookup as long as the signatures survive.
	 */
	private static void resolveActions() {
		if (!FabricLoader.getInstance().isModLoaded(INSPECT_MOD_ID)) {
			disable("Inspect Animations is not installed", null);
			return;
		}
		if (!verifyVersion()) return;
		Class<?> helper;
		try {
			helper = Class.forName(ANIMATION_HELPER_CLASS);
		} catch (Throwable t) {
			disable("Inspect Animations is loaded but class " + ANIMATION_HELPER_CLASS + " is missing", null);
			return;
		}
		for (Method method : helper.getDeclaredMethods()) {
			int modifiers = method.getModifiers();
			if (!Modifier.isStatic(modifiers) || method.getReturnType() != void.class) continue;
			Class<?>[] params = method.getParameterTypes();
			if (params.length == 1 && params[0] == LocalPlayer.class) {
				method.setAccessible(true);
				sendSelectedAnimation = method;
			} else if (params.length == 2 && params[0] == LocalPlayer.class && params[1].isEnum()) {
				method.setAccessible(true);
				sendTurnAnimation = method;
			}
		}
		if (sendTurnAnimation != null) {
			try {
				Class<?> animationType = sendTurnAnimation.getParameterTypes()[1];
				for (Object constant : animationType.getEnumConstants()) {
					if (constant instanceof Enum<?> enumConstant && TURN_ANIMATION_NAME.equals(enumConstant.name())) {
						turnAnimation = constant;
						break;
					}
				}
				if (turnAnimation == null) throw new NoSuchFieldException(TURN_ANIMATION_NAME);
			} catch (Throwable t) {
				sendTurnAnimation = null;
				turnAnimation = null;
				InspectAnimationsIdle.LOGGER.warn("[idle] constant '{}' not found in {}, turn action unavailable", TURN_ANIMATION_NAME, ANIMATION_HELPER_CLASS);
			}
		}
		if (sendSelectedAnimation == null && sendTurnAnimation == null) {
			disable("no compatible Inspect Animations methods found", null);
		}
	}

	/**
	 * One-shot version gate, run before the reflection probe.
	 *
	 * <p>Parses the first three numeric components of the installed version (suffixes like
	 * {@code 0.2.5+26.3} or {@code v0.2.5-beta.1} all reduce cleanly) and rejects anything outside
	 * the supported window. An unparseable version never disables the feature on its own - the
	 * signature probe below still gets a chance and the note is logged instead.
	 *
	 * @return {@code true} to proceed with the probe
	 */
	private static boolean verifyVersion() {
		String version = FabricLoader.getInstance().getModContainer(INSPECT_MOD_ID)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("");
		int[] parts = parseVersion(version);
		if (parts == null) {
			InspectAnimationsIdle.LOGGER.warn("[idle] Inspect Animations version '{}' could not be parsed, skipping version check", version);
			return true;
		}
		if (parts[0] == SUPPORTED_MAJOR && parts[1] == SUPPORTED_MINOR && parts[2] >= SUPPORTED_MIN_PATCH) {
			InspectAnimationsIdle.LOGGER.info("[idle] Inspect Animations {} detected, idle actions available", version);
			return true;
		}
		disable("unsupported Inspect Animations version " + version + " (supported: " + SUPPORTED_VERSION_RANGE + ")", null);
		return false;
	}

	/** First three numeric groups of a version string, or {@code null} if fewer than two can be read. */
	private static int[] parseVersion(String version) {
		if (version.isEmpty()) return null;
		int[] parts = new int[3];
		int index = 0;
		try {
			for (String token : version.split("\\D+")) {
				if (token.isEmpty()) continue;
				parts[index++] = Integer.parseInt(token);
				if (index == 3) return parts;
			}
		} catch (NumberFormatException e) {
			return null;
		}
		return index >= 2 ? parts : null;
	}

	/** Permanent fail-safe: logs the reason once and stops all future triggers. */
	private static void disable(String message, Throwable cause) {
		if (disabled) return;
		disabled = true;
		String version = FabricLoader.getInstance().getModContainer(INSPECT_MOD_ID)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("missing");
		InspectAnimationsIdle.LOGGER.warn("[idle] {} (Inspect Animations {}); idle actions disabled", message, version);
		if (cause != null) InspectAnimationsIdle.LOGGER.warn("[idle] caused by", cause);
	}
}
