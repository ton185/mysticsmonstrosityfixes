package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.example.chaotic_world_content.spells.SwordSpellContainerEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BielGG eagerly gives every sword an empty Iron's Spellbooks container and a
 * {@code BielGGSwordSpellSlot} tag from an item capability, tooltip, or inventory scan. That adds
 * NBT to normal swords and breaks item matching in storage mods. Skip the eager setup entirely.
 */
@Mixin(value = SwordSpellContainerEvents.class, remap = false)
public final class SwordSpellContainerFix {
    @Inject(
            method = "ensureSlot(Lnet/minecraft/world/item/ItemStack;Z)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void mysticmonstrosityfixes$skipEagerSlot(
            ItemStack stack, boolean allowEmpty, CallbackInfo callback
    ) {
        callback.cancel();
    }

    @Inject(
            method = "initializeExistingSwords(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void mysticmonstrosityfixes$skipInventoryScan(TickEvent.PlayerTickEvent event, CallbackInfo callback) {
        callback.cancel();
    }
}
