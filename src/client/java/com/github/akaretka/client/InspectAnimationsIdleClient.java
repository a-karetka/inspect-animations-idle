package com.github.akaretka.client;

import com.github.akaretka.client.idle.IdleConfig;
import com.github.akaretka.client.idle.IdleInspector;
import net.fabricmc.api.ClientModInitializer;

/**
 * Client entrypoint: loads the config before the idle controller starts ticking, so the
 * very first tick already observes final values, then runs the one-shot reflection probe
 * (upstream methods resolved, diagnostics logged) - all of it before the game reaches a world.
 */
public class InspectAnimationsIdleClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		IdleConfig.load();
		IdleInspector.init();
	}
}
