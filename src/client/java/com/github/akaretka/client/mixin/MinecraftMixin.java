package com.github.akaretka.client.mixin;

import com.github.akaretka.client.idle.IdleInspector;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla hooks on {@link Minecraft}, both directions of the idle controller:
 * <ul>
 *   <li>{@code startUseItem} - right click; it is re-invoked every tick the key is held,</li>
 *   <li>{@code startAttack} - left click press,</li>
 *   <li>{@code continueAttack} - left click held (block breaking, repeated attacks),</li>
 *   <li>the {@code tick} tail - the per-tick entry point of the idle controller itself.</li>
 * </ul>
 * Each hook is a config-flag read plus at most one reset, so the per-call cost stays negligible;
 * the tick tail additionally keeps the whole tick path out of Fabric API. The dependency was
 * dropped because the mod used exactly one of its entrypoints ({@code END_CLIENT_TICK}) and
 * Inspect Animations - this mod's own hard dependency - pulls Fabric API into every real
 * install anyway; injecting at the tail of the same method reproduces the event's placement.
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Inject(method = "startUseItem", at = @At("HEAD"))
	private void idle$onUseItem(CallbackInfo ci) {
		IdleInspector.onItemUse();
	}

	@Inject(method = "startAttack", at = @At("HEAD"))
	private void idle$onAttack(CallbackInfoReturnable<Boolean> cir) {
		IdleInspector.onAttack();
	}

	@Inject(method = "continueAttack", at = @At("HEAD"))
	private void idle$onContinueAttack(boolean leftClick, CallbackInfo ci) {
		if (leftClick) IdleInspector.onAttackHold();
	}

	/** The live {@code Minecraft} instance, passed through to the controller like the event used to. */
	@Inject(method = "tick()V", at = @At("TAIL"))
	private void idle$onEndClientTick(CallbackInfo ci) {
		IdleInspector.onClientTick((Minecraft) (Object) this);
	}
}
