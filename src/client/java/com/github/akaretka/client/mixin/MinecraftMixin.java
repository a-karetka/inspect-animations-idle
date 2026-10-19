package com.github.akaretka.client.mixin;

import com.github.akaretka.client.idle.IdleInspector;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Item-use activity sources on {@link Minecraft}. These three methods cover every way a player
 * interacts with their held item without opening a screen:
 * <ul>
 *   <li>{@code startUseItem} - right click; it is re-invoked every tick the key is held,</li>
 *   <li>{@code startAttack} - left click press,</li>
 *   <li>{@code continueAttack} - left click held (block breaking, repeated attacks).</li>
 * </ul>
 * Each hook is a single int store, so the per-call cost is negligible.
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Inject(method = "startUseItem", at = @At("HEAD"))
	private void idle$onUseItem(CallbackInfo ci) {
		IdleInspector.onActivity();
	}

	@Inject(method = "startAttack", at = @At("HEAD"))
	private void idle$onAttack(CallbackInfoReturnable<Boolean> cir) {
		IdleInspector.onActivity();
	}

	@Inject(method = "continueAttack", at = @At("HEAD"))
	private void idle$onContinueAttack(boolean leftClick, CallbackInfo ci) {
		if (leftClick) IdleInspector.onActivity();
	}
}
