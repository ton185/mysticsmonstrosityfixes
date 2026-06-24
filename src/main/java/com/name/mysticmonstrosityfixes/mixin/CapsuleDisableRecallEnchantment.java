package com.name.mysticmonstrosityfixes.mixin;

import capsule.enchantments.RecallEnchant;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(RecallEnchant.class)
public class CapsuleDisableRecallEnchantment {
    /**
     * @author ton185
     * @reason disable the recall enchantment as it causes lag
     */
    @SubscribeEvent
    @Overwrite(remap = false)
    public static void onWorldTickEvent(TickEvent.LevelTickEvent wte) {
    }
}
