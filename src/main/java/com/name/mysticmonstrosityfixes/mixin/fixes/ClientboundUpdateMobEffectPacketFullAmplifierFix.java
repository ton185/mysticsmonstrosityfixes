package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.name.mysticmonstrosityfixes.effect.FullEffectAmplifierPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientboundUpdateMobEffectPacket.class)
public abstract class ClientboundUpdateMobEffectPacketFullAmplifierFix implements FullEffectAmplifierPacket {
    @Unique
    private Integer mmf$fullAmplifier;

    @Inject(method = "<init>(ILnet/minecraft/world/effect/MobEffectInstance;)V", at = @At("TAIL"))
    private void mmf$captureAmplifier(int entityId, MobEffectInstance effect, CallbackInfo ci) {
        this.mmf$fullAmplifier = effect.getAmplifier();
    }

    @Redirect(
            method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/FriendlyByteBuf;readByte()B",
                    ordinal = 0
            ),
            require = 1
    )
    private byte mmf$readAmplifier(FriendlyByteBuf buffer) {
        int amplifier = buffer.readVarInt();
        this.mmf$fullAmplifier = amplifier;
        return (byte) amplifier;
    }

    @Redirect(
            method = "write",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/FriendlyByteBuf;writeByte(I)Lio/netty/buffer/ByteBuf;",
                    ordinal = 0
            ),
            require = 1
    )
    private ByteBuf mmf$writeAmplifier(FriendlyByteBuf buffer, int amplifier) {
        return buffer.writeVarInt(this.mmf$fullAmplifier != null ? this.mmf$fullAmplifier : amplifier);
    }

    @Override
    public int mmf$getEffectAmplifier() {
        return this.mmf$fullAmplifier != null ? this.mmf$fullAmplifier : 0;
    }
}
