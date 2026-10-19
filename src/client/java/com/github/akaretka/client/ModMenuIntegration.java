package com.github.akaretka.client;

import com.github.akaretka.client.screen.IdleScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu integration: exposes IdleScreen for this mod's config button.
 * Loaded only when Mod Menu is installed (its own "modmenu" entrypoint), so the
 * compile-time ModMenu API never leaks into the runtime.
 */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return IdleScreen::new;
	}
}
