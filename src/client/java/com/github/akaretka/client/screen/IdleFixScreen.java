package com.github.akaretka.client.screen;

import com.github.akaretka.client.idle.IdleFixConfig;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Standalone config screen for Mod Menu (plain vanilla widgets, no third-party config library).
 * Values apply immediately; they are persisted when the screen closes ({@link #onClose}),
 * which also covers the Escape key.
 */
public final class IdleFixScreen extends Screen {

	private static final int MIN_SECONDS = IdleFixConfig.MIN_IDLE_SECONDS;
	private static final int MAX_SECONDS = IdleFixConfig.MAX_IDLE_SECONDS;

	private final Screen previous;

	public IdleFixScreen(Screen previous) {
		super(Component.translatable("inspect-animations-fix.idlefix.title"));
		this.previous = previous;
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int top = this.height / 2 - 36;
		addRenderableWidget(Button.builder(toggleMessage(), button -> {
			IdleFixConfig.enabled = !IdleFixConfig.enabled;
			button.setMessage(toggleMessage());
		}).bounds(centerX - 155, top, 150, 20).build());
		addRenderableWidget(new SecondsSlider(centerX + 5, top, 150, 20));
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
				.bounds(centerX - 100, top + 60, 200, 20).build());
	}

	@Override
	public void onClose() {
		IdleFixConfig.save();
		this.minecraft.gui.setScreen(this.previous);
	}

	private static MutableComponent toggleMessage() {
		return Component.translatable("inspect-animations-fix.idlefix.enabled").append(": ")
				.append(IdleFixConfig.enabled ? Component.translatable("inspect-animations-fix.idlefix.on") : Component.translatable("inspect-animations-fix.idlefix.off"));
	}

	private static MutableComponent secondsMessage() {
		return Component.translatable("inspect-animations-fix.idlefix.idle_seconds", IdleFixConfig.idleSeconds);
	}

	private static double toSliderValue(int seconds) {
		return (seconds - MIN_SECONDS) / (double) (MAX_SECONDS - MIN_SECONDS);
	}

	/** Slider over the [MIN_SECONDS; MAX_SECONDS] range, matching IdleFixConfig bounds. */
	private final class SecondsSlider extends AbstractSliderButton {

		private SecondsSlider(int x, int y, int width, int height) {
			super(x, y, width, height, secondsMessage(), toSliderValue(IdleFixConfig.idleSeconds));
		}

		@Override
		protected void updateMessage() {
			setMessage(secondsMessage());
		}

		@Override
		protected void applyValue() {
			int seconds = (int) Math.round(MIN_SECONDS + (MAX_SECONDS - MIN_SECONDS) * value);
			IdleFixConfig.idleSeconds = Math.clamp(seconds, MIN_SECONDS, MAX_SECONDS);
		}
	}
}
