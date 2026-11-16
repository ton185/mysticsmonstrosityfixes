package com.name.mysticmonstrosityfixes.mixin.tweaks;

import com.name.mysticmonstrosityfixes.tweaks.EverlastingAbilitiesUncapper;
import org.cyclops.everlastingabilities.api.Ability;
import org.cyclops.everlastingabilities.api.IAbilityType;
import org.cyclops.everlastingabilities.api.capability.DefaultMutableAbilityStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Raises the cap used when abilities are added to a store. Abilities with a max level of one and
 * Resistance keep the max level they declare.
 */
@Mixin(value = DefaultMutableAbilityStore.class, remap = false)
public abstract class EverlastingAbilitiesUncapTweak {
    @Redirect(
            method = "addAbility",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/cyclops/everlastingabilities/api/IAbilityType;getMaxLevelInfinitySafe()I"
            ),
            remap = false
    )
    private int mysticmonstrosityfixes$uncapMax(IAbilityType abilityType) {
        return EverlastingAbilitiesUncapper.maxLevelOrInfinitySafe(abilityType);
    }

    /**
     * The stock sum is an int addition, which overflows when a level near {@link Integer#MAX_VALUE}
     * is added to an ability that already has levels. Compute the total as a long and clamp it.
     */
    @Redirect(
            method = "addAbility",
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I"),
            remap = false
    )
    private int mysticmonstrosityfixes$clampTotal(int sum, int max, Ability ability) {
        long total = (long) ((DefaultMutableAbilityStore) (Object) this)
                .getAbility(ability.getAbilityType()).getLevel() + ability.getLevel();
        return (int) Math.min(total, (long) max);
    }
}
