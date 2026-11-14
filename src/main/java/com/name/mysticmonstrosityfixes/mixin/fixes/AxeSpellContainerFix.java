package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.example.chaotic_world_content.spells.AxeSpellContainerEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Same eager container and {@code BielGGAxeSpellSlot} setup as the sword variant. Skip it so axes
 * keep their original NBT.
 */
@Mixin(value = AxeSpellContainerEvents.class, remap = false)
public final class AxeSpellContainerFix {
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
            method = "initializeExistingAxes(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void mysticmonstrosityfixes$skipInventoryScan(TickEvent.PlayerTickEvent event, CallbackInfo callback) {
        callback.cancel();
    }
}
