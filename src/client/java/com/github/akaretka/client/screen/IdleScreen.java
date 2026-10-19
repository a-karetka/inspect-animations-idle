package com.github.akaretka.client.screen;

import com.github.akaretka.client.idle.IdleConfig;
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
public final class IdleScreen extends Screen {

	private static final int MIN_SECONDS = IdleConfig.MIN_IDLE_SECONDS;
	private static final int MAX_SECONDS = IdleConfig.MAX_IDLE_SECONDS;

	private final Screen previous;

	public IdleScreen(Screen previous) {
		super(Component.translatable("inspect-animations-idle.idle.title"));
		this.previous = previous;
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int top = this.height / 2 - 36;
		addRenderableWidget(Button.builder(toggleMessage(), button -> {
			IdleConfig.enabled = !IdleConfig.enabled;
			button.setMessage(toggleMessage());
		}).bounds(centerX - 155, top, 150, 20).build());
		addRenderableWidget(new SecondsSlider(centerX + 5, top, 150, 20));
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
				.bounds(centerX - 100, top + 60, 200, 20).build());
	}

	@Override
	public void onClose() {
		IdleConfig.save();
		this.minecraft.gui.setScreen(this.previous);
	}

	private static MutableComponent toggleMessage() {
		return Component.translatable("inspect-animations-idle.idle.enabled").append(": ")
				.append(IdleConfig.enabled ? Component.translatable("inspect-animations-idle.idle.on") : Component.translatable("inspect-animations-idle.idle.off"));
	}

	private static MutableComponent secondsMessage() {
		return Component.translatable("inspect-animations-idle.idle.idle_seconds", IdleConfig.idleSeconds);
	}

	private static double toSliderValue(int seconds) {
		return (seconds - MIN_SECONDS) / (double) (MAX_SECONDS - MIN_SECONDS);
	}

	/** Slider over the [MIN_SECONDS; MAX_SECONDS] range, matching IdleConfig bounds. */
	private final class SecondsSlider extends AbstractSliderButton {

		private SecondsSlider(int x, int y, int width, int height) {
			super(x, y, width, height, secondsMessage(), toSliderValue(IdleConfig.idleSeconds));
		}

		@Override
		protected void updateMessage() {
			setMessage(secondsMessage());
		}

		@Override
		protected void applyValue() {
			int seconds = (int) Math.round(MIN_SECONDS + (MAX_SECONDS - MIN_SECONDS) * value);
			IdleConfig.apply(seconds);
		}
	}
}
