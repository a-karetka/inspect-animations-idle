package com.github.akaretka.client;

import com.github.akaretka.client.screen.IdleConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu integration: exposes the YetAnotherConfigLib-based config screen for this
 * mod's config button. Loaded only when Mod Menu is installed (its own "modmenu"
 * entrypoint), so the compile-time ModMenu API never leaks into the runtime.
 */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> IdleConfigScreen.build().generateScreen(parent);
	}
}
