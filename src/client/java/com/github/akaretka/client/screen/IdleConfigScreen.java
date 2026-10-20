package com.github.akaretka.client.screen;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.github.akaretka.client.idle.IdleConfig;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.StateManager;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import net.minecraft.network.chat.Component;

/**
 * Config screen for Mod Menu, built with YetAnotherConfigLib - the same config library
 * Inspect Animations itself builds its menu on, so both mods share one visual style,
 * narration and interaction model (scrollable option list, group folding, YACL's
 * built-in per-option reset button, save/cancel footer).
 *
 * <p>Why YACL is compile-only: it is NOT bundled. It is declared as our own hard
 * {@code depends} in {@code fabric.mod.json} - redundant belt-and-braces with Inspect
 * Animations (our hard {@code depends}), which itself hard-depends on
 * {@code yet_another_config_lib_v3}, so YACL is present in any loadable combination
 * either way.
 *
 * <p>Layout: two YACL categories (tabs). The main tab holds the user-facing basics
 * (master switch, both timeouts); the "experimental" tab holds the twelve trigger
 * switches plus the head-turn threshold - tuning knobs that a 1.0.x user never needs.
 * Every option carries a description shown under the entry name; trigger descriptions
 * live in lang as {@code idle.trigger.<name>.tooltip}.
 *
 * <p>All bindings are instant state managers ({@code StateManager.createInstant}): a
 * change lands in config memory immediately and the tick-domain caches refresh through
 * {@code applyFirst}/{@code applyNext}/{@code applyHeadTurn}, so a charged idle window
 * follows edits live. Persistence is YACL's job: its save/cancel footer calls
 * {@link IdleConfig#save()} through {@code YetAnotherConfigLib$Builder.save}.
 */
public final class IdleConfigScreen {

	private IdleConfigScreen() {
	}

	/** Hook used by Mod Menu; the parent screen comes from wherever the menu was opened. */
	public static YetAnotherConfigLib build() {
		return YetAnotherConfigLib.createBuilder()
				.title(tr("idle.title"))
				.save(IdleConfig::save)
				.category(ConfigCategory.createBuilder()
						.name(tr("category.main"))
						.group(group("idle.section.timing")
								.option(booleanOption("idle.enabled", true,
										() -> IdleConfig.enabled, v -> IdleConfig.enabled = v,
										"idle.enabled.tooltip"))
								.option(secondsSlider("idle.first_seconds", IdleConfig.DEFAULT_FIRST_SECONDS,
										"idle.first_seconds.tooltip",
										() -> IdleConfig.firstSeconds, IdleConfig::applyFirst))
								.option(secondsSlider("idle.next_seconds", IdleConfig.DEFAULT_NEXT_SECONDS,
										"idle.next_seconds.tooltip",
										() -> IdleConfig.nextSeconds, IdleConfig::applyNext))
								.build())
						.build())
				// Triggers live on a second tab: they are tuning knobs, not basics, and a
				// short main tab keeps the overall look close to Inspect Animations.
				.category(ConfigCategory.createBuilder()
						.name(tr("category.experimental"))
						.group(group("idle.section.actions")
								.options(List.of(
										// Item-side triggers match their IdleConfig defaults: on in 1.1.0.
										new Toggle("idle.trigger.slot_switch", true,
												() -> IdleConfig.resetSlotSwitch, v -> IdleConfig.resetSlotSwitch = v)
												.option(),
										new Toggle("idle.trigger.item_use", true,
												() -> IdleConfig.resetItemUse, v -> IdleConfig.resetItemUse = v)
												.option(),
										new Toggle("idle.trigger.attack", true,
												() -> IdleConfig.resetAttack, v -> IdleConfig.resetAttack = v)
												.option(),
										new Toggle("idle.trigger.attack_hold", true,
												() -> IdleConfig.resetAttackHold, v -> IdleConfig.resetAttackHold = v)
												.option(),
										new Toggle("idle.trigger.offhand_swap", true,
												() -> IdleConfig.resetOffhandSwap, v -> IdleConfig.resetOffhandSwap = v)
												.option(),
										new Toggle("idle.trigger.item_replace", true,
												() -> IdleConfig.resetItemReplace, v -> IdleConfig.resetItemReplace = v)
												.option()))
								.build())
						.group(group("idle.section.states")
								.options(List.of(
										new Toggle("idle.trigger.sprint", false,
												() -> IdleConfig.resetSprint, v -> IdleConfig.resetSprint = v)
												.option(),
										new Toggle("idle.trigger.walk", false,
												() -> IdleConfig.resetWalk, v -> IdleConfig.resetWalk = v)
												.option(),
										new Toggle("idle.trigger.crouch", false,
												() -> IdleConfig.resetCrouch, v -> IdleConfig.resetCrouch = v)
												.option(),
										new Toggle("idle.trigger.head_turn", false,
												() -> IdleConfig.resetHeadTurn, v -> IdleConfig.resetHeadTurn = v)
												.option(),
										// The threshold sits right under its switch: same parameter.
										degreesSlider("idle.head_turn_degrees", IdleConfig.DEFAULT_HEAD_TURN_DEGREES,
												"idle.head_turn_degrees.tooltip",
												() -> IdleConfig.headTurnDegrees, IdleConfig::applyHeadTurn),
										new Toggle("idle.trigger.creative_fly", false,
												() -> IdleConfig.resetCreativeFly, v -> IdleConfig.resetCreativeFly = v)
												.option(),
										new Toggle("idle.trigger.elytra_fly", false,
												() -> IdleConfig.resetElytraFly, v -> IdleConfig.resetElytraFly = v)
												.option()))
								.build())
						.build())
				.build();
	}

	private static Component tr(String key) {
		return Component.translatable("inspect-animations-idle." + key);
	}

	private static OptionGroup.Builder group(String key) {
		return OptionGroup.createBuilder().name(tr(key));
	}

	/**
	 * Instant state managers ({@code StateManager.createInstant}) make writes land in
	 * {@link IdleConfig} the moment the user moves a control, so the charged idle window
	 * follows edits live. The plain `.instant` flag is deprecated in YACL 3.9.7 - it
	 * logs an ERROR on every screen build. Used for the master switch; trigger switches
	 * go through {@link Toggle}, whose default is always {@code false}.
	 */
	private static Option<Boolean> booleanOption(String key, boolean defaultValue,
			Supplier<Boolean> get, Consumer<Boolean> set, String descriptionKey) {
		return Option.<Boolean>createBuilder()
				.name(tr(key))
				.stateManager(StateManager.createInstant(defaultValue, get, set))
				.description(OptionDescription.of(tr(descriptionKey)))
				// Coloured tick: on/off highlighted, matching the common YACL look.
				.controller(o -> BooleanControllerBuilder.create(o).coloured(true))
				.build();
	}

	private static Option<Integer> secondsSlider(String key, int defaultValue, String descriptionKey,
			Supplier<Integer> get, Consumer<Integer> set) {
		return intSlider(key, defaultValue, descriptionKey,
				IdleConfig.MIN_IDLE_SECONDS, IdleConfig.MAX_IDLE_SECONDS,
				"idle.seconds_value", get, set);
	}

	private static Option<Integer> degreesSlider(String key, int defaultValue, String descriptionKey,
			Supplier<Integer> get, Consumer<Integer> set) {
		return intSlider(key, defaultValue, descriptionKey,
				IdleConfig.MIN_HEAD_TURN_DEGREES, IdleConfig.MAX_HEAD_TURN_DEGREES,
				"idle.degrees_value", get, set);
	}

	private static Option<Integer> intSlider(String key, int defaultValue, String descriptionKey,
			int min, int max, String suffixKey, Supplier<Integer> get, Consumer<Integer> set) {
		return Option.<Integer>createBuilder()
				.name(tr(key))
				.description(OptionDescription.of(tr(descriptionKey)))
				.stateManager(StateManager.createInstant(defaultValue, get, set))
				.controller(o -> IntegerSliderControllerBuilder.create(o)
						.range(min, max)
						.step(1)
						// The caption names the parameter; the slider only prints the value.
						.formatValue(v -> Component.translatable("inspect-animations-idle." + suffixKey, v)))
				.build();
	}

	/** One boolean trigger: its lang keys, its default value and accessors in {@link IdleConfig}. */
	private record Toggle(String key, boolean defaultValue, Supplier<Boolean> get, Consumer<Boolean> set) {

		/** Builds the trigger with its {@code idle.trigger.<name>.tooltip} description. */
		Option<Boolean> option() {
			return Option.<Boolean>createBuilder()
					.name(tr(key))
					.description(OptionDescription.of(tr(key + ".tooltip")))
					.stateManager(StateManager.createInstant(defaultValue, get, set))
					.controller(o -> BooleanControllerBuilder.create(o).coloured(true))
					.build();
		}
	}
}
