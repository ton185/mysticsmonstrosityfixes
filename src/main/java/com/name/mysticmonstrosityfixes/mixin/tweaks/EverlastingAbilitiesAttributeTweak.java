package com.name.mysticmonstrosityfixes.mixin.tweaks;

import com.name.mysticmonstrosityfixes.tweaks.EverlastingAbilitiesUncapper;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.cyclops.everlastingabilities.ability.AbilityTypeAttributeModifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;

/**
 * Attribute abilities build one modifier per level up to their max level. This resolves a
 * modifier for levels above that map so uncapped attribute abilities keep scaling.
 */
@Mixin(value = AbilityTypeAttributeModifier.class, remap = false)
public abstract class EverlastingAbilitiesAttributeTweak {
    @Shadow
    @Final
    private Map<Integer, AttributeModifier> attributeModifiers;

    @Redirect(
            method = {"onTick", "onChangedLevel"},
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"
            ),
            remap = false
    )
    private Object mysticmonstrosityfixes$resolveModifier(Map<Integer, AttributeModifier> modifiers, Object level) {
        return EverlastingAbilitiesUncapper.modifierForLevel(
                (AbilityTypeAttributeModifier) (Object) this, (Integer) level, this.attributeModifiers);
    }
}
