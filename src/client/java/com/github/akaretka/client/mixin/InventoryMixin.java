package com.github.akaretka.client.mixin;

import com.github.akaretka.client.idle.IdleInspector;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Item-switching and held-item-replacement sources on {@link Inventory}:

 * <ul>
 *   <li>{@code setSelectedSlot} - hotbar switching funnels through it for all sources
 *       (keys 1-9, mouse wheel, programmatic selection), so this single hook covers every
 *       "item switch" case; the offhand swap is handled separately in IdleInspector's tick,</li>
 *   <li>{@code setItem} - the stack in a container slot changed (pickup, placement, drop,
 *       any inventory click). Only the selected hotbar slot and the offhand slot (40) count
 *       as "held item replaced"; armor and storage slots are filtered out here, so the idle
 *       controller never sees them.</li>
 * </ul>

 * Both hooks stay flag-read + int-compare + one static call.

 */

@Mixin(Inventory.class)
public class InventoryMixin {
	@Inject(method = "setSelectedSlot", at = @At("HEAD"))
	private void idle$onSelectSlot(CallbackInfo ci) {
		IdleInspector.onSlotSwitch();
	}

	/**
	 * Slot indices are compared against the live selection at call time, so a replacement
	 * that races a pending selection change still resets exactly the slot that ends up
	 * in hand. Slot 40 is the offhand container slot; nothing above 8 except it qualifies.
	 * The {@code stack} parameter is unused but must stay: an @Inject handler has to accept
	 * the full target argument list — a partial capture fails at mixin apply and, with
	 * defaultRequire 1, crashes the game (verified the hard way in the first 1.1.0 run).
	 */
	@Inject(method = "setItem", at = @At("HEAD"))
	private void idle$onSetItem(int slotIndex, ItemStack stack, CallbackInfo ci) {
		if (slotIndex == ((Inventory) (Object) this).getSelectedSlot() || slotIndex == 40) {
			IdleInspector.onItemReplace();
		}
	}
}
