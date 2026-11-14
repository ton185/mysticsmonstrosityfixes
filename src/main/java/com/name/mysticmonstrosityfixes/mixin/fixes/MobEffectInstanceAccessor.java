package com.name.mysticmonstrosityfixes.mixin.fixes;

import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MobEffectInstance.class)
public interface MobEffectInstanceAccessor {
    @Accessor("hiddenEffect")
    MobEffectInstance mysticmonstrosityfixes$getHiddenEffect();

    @Accessor("hiddenEffect")
    void mysticmonstrosityfixes$setHiddenEffect(MobEffectInstance hiddenEffect);
}
