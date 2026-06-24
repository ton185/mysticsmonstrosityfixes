package com.name.mysticmonstrosityfixes.mixin;

import capsule.enchantments.RecallEnchant;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(RecallEnchant.class)
public class CapsuleDisableRecallEnchantment {
    /**
     * @author ton185
     * @reason disable the recall enchantment as it causes lag
     */
    @Overwrite
    public static void onWorldTickEvent(TickEvent.LevelTickEvent wte) {
    }
}
