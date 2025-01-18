package com.name.mysticmonstrosityfixes.mixin;

import net.jitl.client.stats.PacketPlayerStats;
import net.jitl.common.capability.stats.PlayerStatsProvider;
import net.minecraft.client.Minecraft;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Mixin(PacketPlayerStats.class)
public class JITLDeathCrashFix {
    @Inject(at = @At("HEAD"), cancellable = true, remap = false, method = "handle")
    public void nullFix(Supplier<NetworkEvent.Context> ctx, CallbackInfo ci) {
        if (Minecraft.getInstance().player != null && !Minecraft.getInstance().player.getCapability(PlayerStatsProvider.PLAYER_STATS).isPresent()) {
            ctx.get().setPacketHandled(true);
            ci.cancel();
        }
    }
}
