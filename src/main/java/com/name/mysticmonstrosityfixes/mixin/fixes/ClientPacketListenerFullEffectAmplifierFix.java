package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.name.mysticmonstrosityfixes.effect.FullEffectAmplifierPacket;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerFullEffectAmplifierFix {
    @Unique
    private Integer mmf$fullAmplifier;

    @Redirect(
            method = "handleUpdateMobEffect",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/game/ClientboundUpdateMobEffectPacket;getEffectAmplifier()B"
            )
    )
    private byte mmf$captureFullAmplifier(ClientboundUpdateMobEffectPacket packet) {
        this.mmf$fullAmplifier = packet instanceof FullEffectAmplifierPacket access
                ? access.mmf$getEffectAmplifier()
                : (int) packet.getEffectAmplifier();
        return packet.getEffectAmplifier();
    }

    @ModifyArg(
            method = "handleUpdateMobEffect",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/effect/MobEffectInstance;<init>(Lnet/minecraft/world/effect/MobEffect;IIZZZLnet/minecraft/world/effect/MobEffectInstance;Ljava/util/Optional;)V"
            ),
            index = 2
    )
    private int mmf$useFullAmplifier(int amplifier) {
        Integer fullAmplifier = this.mmf$fullAmplifier;
        this.mmf$fullAmplifier = null;
        return fullAmplifier != null ? fullAmplifier : amplifier;
    }
}
