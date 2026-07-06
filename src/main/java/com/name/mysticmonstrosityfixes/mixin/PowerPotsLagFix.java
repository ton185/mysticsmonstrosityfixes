package com.name.mysticmonstrosityfixes.mixin;

import com.leo.powerpots.block.entity.PowerPotBE;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PowerPotBE.class)
public class PowerPotsLagFix {

    @Unique
    private int syncCooldown = 0;

    @Inject(method = "sync", at = @At("HEAD"), cancellable = true, remap = false)
    private void throttleSync(CallbackInfo ci) {
        if (this.syncCooldown > 0) {
            this.syncCooldown--;
            ci.cancel();
        } else {
            this.syncCooldown = 3;
        }
    }
}
