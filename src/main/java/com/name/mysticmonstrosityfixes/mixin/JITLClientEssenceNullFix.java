package com.name.mysticmonstrosityfixes.mixin;

import net.jitl.client.essence.ClientEssence;
import net.jitl.common.capability.essence.PlayerEssenceProvider;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientEssence.class)
public class JITLClientEssenceNullFix {
    @Inject(at = @At("HEAD"), method = "setClientEssence", cancellable = true, remap = false)
    private static void setClientEssenceWithNullFix(float value, CallbackInfo ci) {
        if (Minecraft.getInstance().player == null || !Minecraft.getInstance().player.getCapability(PlayerEssenceProvider.PLAYER_ESSENCE).isPresent()) {
            ci.cancel();
        }
    }

    @Inject(at = @At("HEAD"), method = "setClientBurnout", cancellable = true, remap = false)
    private static void setClientBurnoutWithNullFix(float value, CallbackInfo ci) {
        if (Minecraft.getInstance().player == null || !Minecraft.getInstance().player.getCapability(PlayerEssenceProvider.PLAYER_ESSENCE).isPresent()) {
            ci.cancel();
        }
    }
}
