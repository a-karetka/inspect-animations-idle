package com.github.akaretka;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entrypoint (shared source set). The mod itself is fully client-side; the main entrypoint
 * only exists so the jar loads cleanly in any environment, holding the shared LOGGER.
 */
public class InspectAnimationsFix implements ModInitializer {
	public static final String MOD_ID = "inspect-animations-fix";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// No server-side behavior: all logic lives in the client entrypoint.
	}
}
