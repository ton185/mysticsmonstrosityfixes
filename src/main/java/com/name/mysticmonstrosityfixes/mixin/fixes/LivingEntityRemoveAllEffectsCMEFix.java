package com.name.mysticmonstrosityfixes.mixin.fixes;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;

/**
 * Forge's {@code LivingEntity#removeAllEffects()} iterates the live {@code activeEffects} map and
 * posts a {@code MobEffectEvent.Remove} for each effect before removing it. A Remove listener can
 * add an effect back into that map, which invalidates the iterator and makes the following
 * {@code iterator.remove()} throw {@code ConcurrentModificationException} from the server tick
 * loop.
 * <p>
 * Mowzie's Mobs triggers this from its Eclipsed effect handler: adding Eclipsed stores the current
 * effects and then calls {@code removeAllEffects()}, whose Remove events can restore effects while
 * the map is being iterated. Remove from a snapshot instead, so listeners may add or remove
 * effects during the cleanup without breaking it.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityRemoveAllEffectsCMEFix {
    @Inject(method = "removeAllEffects", at = @At("HEAD"), cancellable = true)
    private void mysticmonstrosityfixes$removeAllEffectsFromSnapshot(CallbackInfoReturnable<Boolean> callback) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity.level().isClientSide) {
            callback.setReturnValue(false);
            return;
        }
        boolean removed = false;
        for (MobEffectInstance effect : new ArrayList<>(entity.getActiveEffects())) {
            removed |= entity.removeEffect(effect.getEffect());
        }
        callback.setReturnValue(removed);
    }
}
