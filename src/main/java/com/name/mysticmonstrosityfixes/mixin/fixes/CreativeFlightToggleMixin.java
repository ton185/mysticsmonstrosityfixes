package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.name.mysticmonstrosityfixes.flight.CreativeFlightGrants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The server drops a flight toggle when {@code mayfly} was cleared earlier in the tick.
 * A granted player is allowed to start and stop flying, and that choice is kept.
 * Priority 2000 is applied before the usual mixins, so this head runs just before the check.
 */
@Mixin(value = ServerGamePacketListenerImpl.class, priority = 2000)
public class CreativeFlightToggleMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handlePlayerAbilities", at = @At("HEAD"))
    private void mysticmonstrosityfixes$allowGrantedFlight(ServerboundPlayerAbilitiesPacket packet, CallbackInfo ci) {
        if (this.player.isCreative() || this.player.isSpectator() || CreativeFlightGrants.granted(this.player)) {
            this.player.getAbilities().mayfly = true;
        }
        CreativeFlightGrants.rememberToggle(this.player, packet.isFlying());
    }

    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void mysticmonstrosityfixes$keepFlightForMove(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        if (this.player != null) {
            CreativeFlightGrants.allowMovement(this.player);
        }
    }

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void mysticmonstrosityfixes$forgetFlight(Component reason, CallbackInfo ci) {
        if (this.player != null) {
            CreativeFlightGrants.forget(this.player);
        }
    }
}
