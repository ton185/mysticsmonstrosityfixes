package com.name.mysticmonstrosityfixes.mixin.tweaks;

import cn.sd.jrz.alltheimbaium.setup.SupplyEvents;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips Alltheimbaium's achievement supply point reward, which also suppresses its chat message.
 */
@Mixin(value = SupplyEvents.class, remap = false)
public abstract class AllTheImbaiumSupplyEventsTweak {
    @Inject(method = "onAdvancementEarned", at = @At("HEAD"), cancellable = true, remap = false)
    private static void mysticmonstrosityfixes$disableAdvancementGain(
            AdvancementEvent.AdvancementEarnEvent event, CallbackInfo callback) {
        callback.cancel();
    }
}
