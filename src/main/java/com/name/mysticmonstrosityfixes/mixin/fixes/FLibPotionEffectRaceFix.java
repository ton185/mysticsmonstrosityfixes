package com.name.mysticmonstrosityfixes.mixin.fixes;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * FLib checks {@code hasEffect} and then calls {@code getEffect} for the same effect. When the
 * effect is removed in between those calls, the second lookup returns null and the merge crashes
 * on the amplifier or duration read. Do the lookup once and fall back to adding the new effect
 * when it is already gone.
 * <p>
 * The merged instance copies every detail of the existing effect, including ambient, visibility,
 * hidden effects, and curative items. Only the duration and amplifier change.
 */
@Mixin(targets = "com.lothrazar.library.util.EntityUtil", remap = false)
public class FLibPotionEffectRaceFix {
    @Inject(
            method = "addOrMergePotionEffect(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/effect/MobEffectInstance;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void mysticmonstrosityfixes$mergePotionEffect(
            LivingEntity entity, MobEffectInstance added, CallbackInfo callback
    ) {
        callback.cancel();
        if (entity == null || added == null) {
            return;
        }
        MobEffect effect = added.getEffect();
        if (effect == null) {
            return;
        }
        MobEffectInstance existing = entity.getEffect(effect);
        if (existing == null) {
            entity.addEffect(added);
            return;
        }
        int amplifier = Math.max(existing.getAmplifier(), added.getAmplifier());
        int duration = added.getDuration() + existing.getDuration();
        MobEffectInstance merged = new MobEffectInstance(
                existing.getEffect(),
                duration,
                amplifier,
                existing.isAmbient(),
                existing.isVisible(),
                existing.showIcon(),
                copyHidden(existing),
                existing.getFactorData()
        );
        List<ItemStack> curativeItems = existing.getCurativeItems();
        if (curativeItems != null) {
            merged.setCurativeItems(new ArrayList<>(curativeItems));
        }
        entity.addEffect(merged);
    }

    @Unique
    private static MobEffectInstance copyHidden(MobEffectInstance source) {
        MobEffectInstance hidden = ((MobEffectInstanceAccessor) (Object) source)
                .mysticmonstrosityfixes$getHiddenEffect();
        if (hidden == null) {
            return null;
        }
        MobEffectInstance copy = new MobEffectInstance(hidden);
        ((MobEffectInstanceAccessor) (Object) copy)
                .mysticmonstrosityfixes$setHiddenEffect(copyHidden(hidden));
        return copy;
    }
}
