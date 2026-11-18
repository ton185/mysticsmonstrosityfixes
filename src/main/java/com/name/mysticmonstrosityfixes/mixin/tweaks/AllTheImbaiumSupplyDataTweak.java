package com.name.mysticmonstrosityfixes.mixin.tweaks;

import cn.sd.jrz.alltheimbaium.setup.SupplyData;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Disables Alltheimbaium supply point gains and makes every read report zero points, so the supply
 * crate cannot accumulate or spend anything.
 */
@Mixin(value = SupplyData.class, remap = false)
public abstract class AllTheImbaiumSupplyDataTweak {
    @Inject(method = "tickSecond", at = @At("HEAD"), cancellable = true, remap = false)
    private static void mysticmonstrosityfixes$disableTimedGain(Player player, CallbackInfo callback) {
        callback.cancel();
    }

    @Inject(method = "addMax", at = @At("HEAD"), cancellable = true, remap = false)
    private static void mysticmonstrosityfixes$disableGain(Player player, int amount, CallbackInfo callback) {
        callback.cancel();
    }

    @Inject(method = "getMax", at = @At("HEAD"), cancellable = true, remap = false)
    private static void mysticmonstrosityfixes$zeroMax(Player player, CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(0);
    }

    @Inject(method = "getUsed", at = @At("HEAD"), cancellable = true, remap = false)
    private static void mysticmonstrosityfixes$zeroUsed(Player player, CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(0);
    }

    @Inject(method = "getRemaining", at = @At("HEAD"), cancellable = true, remap = false)
    private static void mysticmonstrosityfixes$zeroRemaining(Player player, CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(0);
    }
}
