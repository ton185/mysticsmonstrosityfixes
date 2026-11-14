package com.name.mysticmonstrosityfixes.mixin.fixes;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import org.cyclops.everlastingabilities.ability.AbilityTypeAttributeModifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Everlasting Abilities applies attribute bonuses as transient modifiers and re-adds them from
 * {@code onTick}. Other mods that rebuild or copy attributes can drop those modifiers, so the
 * bonus is removed and re-added every tick. Use permanent modifiers instead and only touch them
 * when the ability level changes or when a player joins, which lets the normal attribute
 * synchronization and save data keep them in place.
 */
@Mixin(value = AbilityTypeAttributeModifier.class, remap = false)
public abstract class EverlastingAbilitiesAttributeFix {
    @Shadow
    @Final
    private Attribute attribute;

    @Shadow
    @Final
    private Map<Integer, AttributeModifier> attributeModifiers;

    @Unique
    private static final Map<Player, Set<UUID>> mysticmonstrosityfixes$applied = new WeakHashMap<>();

    @Inject(
            method = "onTick(Lnet/minecraft/world/entity/player/Player;I)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void mysticmonstrosityfixes$applyOnceAfterJoin(Player player, int level, CallbackInfo callback) {
        callback.cancel();
        if (player.level().isClientSide) {
            return;
        }
        AttributeModifier modifier = this.attributeModifiers.get(level);
        if (modifier == null) {
            return;
        }
        AttributeInstance instance = player.getAttribute(this.attribute);
        if (instance == null) {
            return;
        }
        Set<UUID> applied = mysticmonstrosityfixes$applied.get(player);
        if (applied == null) {
            applied = new HashSet<>();
            mysticmonstrosityfixes$applied.put(player, applied);
        }
        if (!applied.add(modifier.getId())) {
            return;
        }
        this.mysticmonstrosityfixes$removeStale(instance, modifier);
        if (!instance.hasModifier(modifier)) {
            instance.addPermanentModifier(modifier);
        }
    }

    @Inject(
            method = "onChangedLevel(Lnet/minecraft/world/entity/player/Player;II)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void mysticmonstrosityfixes$applyPermanentModifier(
            Player player, int oldLevel, int newLevel, CallbackInfo callback
    ) {
        callback.cancel();
        if (player.level().isClientSide) {
            return;
        }
        AttributeInstance instance = player.getAttribute(this.attribute);
        if (instance == null) {
            return;
        }
        this.mysticmonstrosityfixes$removeStale(instance, null);
        if (newLevel > 0) {
            AttributeModifier modifier = this.attributeModifiers.get(newLevel);
            if (modifier != null) {
                instance.addPermanentModifier(modifier);
            }
        }
    }

    @Unique
    private void mysticmonstrosityfixes$removeStale(AttributeInstance instance, AttributeModifier keep) {
        String prefix = "everlastingabilities:modifier_"
                + ((AbilityTypeAttributeModifier) (Object) this).getTranslationKey() + "_";
        for (AttributeModifier existing : instance.getModifiers()) {
            if (existing.getName().startsWith(prefix)
                    && (keep == null || !existing.getId().equals(keep.getId()))) {
                instance.removeModifier(existing);
            }
        }
    }
}
