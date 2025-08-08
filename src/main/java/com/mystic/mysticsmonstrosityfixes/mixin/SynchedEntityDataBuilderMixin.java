package com.mystic.mysticsmonstrosityfixes.mixin;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.DataItem;
import net.minecraft.network.syncher.SyncedDataHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

/**
 * Fixes crashes when a class' ID_REGISTRY count is inflated (e.g., Boat -> 17)
 * but its defineSynchedData only fills 0..14. Vanilla build() throws on the
 * first null slot. We trim ONLY trailing nulls (no "holes") before build().
 *
 * Safety:
 * - If there is any null BEFORE the last non-null index, we do nothing and let
 *   vanilla throw (that indicates a real missing field).
 * - If all nulls are trailing, we shrink the array in-place.
 */
@Mixin(SynchedEntityData.Builder.class)
public class SynchedEntityDataBuilderMixin {
    @Shadow @Final private SyncedDataHolder entity;

    @Shadow @Final @Mutable
    private DataItem<?>[] itemsById;

    @Inject(method = "build", at = @At("HEAD"))
    private void mysticsmonstrosityfixes$stripTrailingNulls(CallbackInfoReturnable<SynchedEntityData> cir) {
        // Find the highest populated index
        int highest = -1;
        for (int i = 0; i < itemsById.length; i++) {
            if (itemsById[i] != null) highest = i;
        }

        // If the last slot is already populated, nothing to do.
        if (highest == itemsById.length - 1) return;

        // If any null appears BEFORE 'highest', that's a hole: keep vanilla behavior.
        for (int i = 0; i <= highest; i++) {
            if (itemsById[i] == null) return;
        }

        // Only trailing nulls -> shrink the array so vanilla build() is happy.
        if (highest >= 0) {
            itemsById = Arrays.copyOf(itemsById, highest + 1);
        }
    }
}
