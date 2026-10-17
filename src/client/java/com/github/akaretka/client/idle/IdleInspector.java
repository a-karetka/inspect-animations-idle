package com.github.akaretka.client.idle;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.ThreadLocalRandom;

import com.github.akaretka.InspectAnimationsFix;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Triggers one of the Inspect Animations actions after the configured idle timeout.
 *
 * <p>The integration is a runtime probe instead of a direct dependency: Inspect Animations
 * does not expose a public API, so its package-private {@code AnimationHelper.sendAnimation}
 * methods are resolved once via reflection and re-played exactly like a key press would.
 * Found only on the client thread; the per-tick hot path keeps allocations and syscalls out.
 * If Inspect Animations is missing or incompatible, the feature disables itself instead of crashing.
 */
public final class IdleInspector {

	/** Mod id of Inspect Animations (without dash, as declared in its fabric.mod.json). */
	private static final String INSPECT_MOD_ID = "inspectanimations";
	/** Integration target; resolved through reflection because the class is private to the other mod. */
	private static final String ANIMATION_HELPER_CLASS = "net.soundsofthesun.inspectanimations.client.AnimationHelper";
	private static final String TURN_ANIMATION_NAME = "TURN";

	/** Idle counter in client ticks; the timer restarts after every auto-trigger. */
	private static int idleTicks;
	/** Set once the reflection probe has run; the trigger path must not repeat it. */
	private static boolean resolved;
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

	/** Registers the tick handler; called once from the client entrypoint. */
	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(IdleInspector::tick);
	}

	/**
	 * Called from mixins on item use and item switching; the whole hook is one int store,
	 * which is what makes the mixins effectively free.
	 */
	public static void onActivity() {
		idleTicks = 0;
	}

	private static void tick(Minecraft minecraft) {
		// Cheapest possible tick path: static flag read, counter increment, comparison.
		// Disabling resets the counter so that re-enabling starts a fresh window.
		if (!IdleFixConfig.enabled) {
			idleTicks = 0;
			return;
		}
		// Offhand swap (F) also switches the held item but never passes through
		// Inventory#setSelectedSlot, which the mixins already cover.
		// A key press leaves the mapping "down" only while the game is in-game (no screen open),
		// so this produces no false activity from GUIs or the title screen.
		if (minecraft.options.keySwapOffhand.isDown()) idleTicks = 0;
		if (++idleTicks < IdleFixConfig.idleSeconds * 20) return;
		// Reset first so a failing invocation below can never cause a cascade of triggers.
		idleTicks = 0;
		// Triggers are skipped in menus and while the game is paused; the counter itself
		// keeps running intentionally, per the agreed behaviour.
		if (minecraft.player == null || minecraft.level == null || minecraft.isPaused()) return;
		if (!resolved) resolveActions();
		if (disabled) return;
		Method method = pickAction();
		if (method == null) return;
		try {
			if (method == sendTurnAnimation) method.invoke(null, minecraft.player, turnAnimation);
			else method.invoke(null, minecraft.player);
		} catch (Throwable t) {
			disable("Inspect Animations invocation failed", t);
		}
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
		resolved = true;
		if (!FabricLoader.getInstance().isModLoaded(INSPECT_MOD_ID)) {
			disable("Inspect Animations is not installed", null);
			return;
		}
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
				InspectAnimationsFix.LOGGER.warn("[idle] constant '{}' not found in {}, turn action unavailable", TURN_ANIMATION_NAME, ANIMATION_HELPER_CLASS);
			}
		}
		if (sendSelectedAnimation == null && sendTurnAnimation == null) {
			disable("no compatible Inspect Animations methods found", null);
		}
	}

	/** Permanent fail-safe: logs the reason once and stops all future triggers. */
	private static void disable(String message, Throwable cause) {
		if (disabled) return;
		disabled = true;
		String version = FabricLoader.getInstance().getModContainer(INSPECT_MOD_ID)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("missing");
		InspectAnimationsFix.LOGGER.warn("[idle] {} (Inspect Animations {}); idle actions disabled", message, version);
		if (cause != null) InspectAnimationsFix.LOGGER.warn("[idle] caused by", cause);
	}
}
