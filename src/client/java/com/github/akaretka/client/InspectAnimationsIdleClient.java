package com.github.akaretka.client;

import com.github.akaretka.client.idle.IdleConfig;
import com.github.akaretka.client.idle.IdleInspector;
import net.fabricmc.api.ClientModInitializer;

/**
 * Client entrypoint: loads the config before the idle controller registers its tick handler,
 * so the very first tick already observes final values.
 */
public class InspectAnimationsIdleClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		IdleConfig.load();
		IdleInspector.init();
	}
}
