package com.github.akaretka.client.mixin;

import com.github.akaretka.client.idle.IdleInspector;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hotbar switching funnels through {@link Inventory#setSelectedSlot} for all sources
 * (keys 1-9, mouse wheel, programmatic selection), so this single hook covers every
 * "item switch" case; the offhand swap is handled separately in IdleInspector's tick.
 */
@Mixin(Inventory.class)
public class InventoryMixin {
	@Inject(method = "setSelectedSlot", at = @At("HEAD"))
	private void inspectfix$onSelectSlot(CallbackInfo ci) {
		IdleInspector.onActivity();
	}
}
