package com.name.mysticmonstrosityfixes.mixin.tweaks;

import com.name.mysticmonstrosityfixes.tweaks.EverlastingAbilitiesUncapper;
import org.cyclops.everlastingabilities.api.IAbilityType;
import org.cyclops.everlastingabilities.command.CommandModifyAbilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets the abilities command add levels past the cap under the same rules as the ability store.
 */
@Mixin(value = CommandModifyAbilities.class, remap = false)
public abstract class EverlastingAbilitiesCommandUncapTweak {
    @Redirect(
            method = "run",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/cyclops/everlastingabilities/api/IAbilityType;getMaxLevelInfinitySafe()I"
            ),
            remap = false
    )
    private int mysticmonstrosityfixes$uncapCommandMax(IAbilityType abilityType) {
        return EverlastingAbilitiesUncapper.maxLevelOrInfinitySafe(abilityType);
    }
}
