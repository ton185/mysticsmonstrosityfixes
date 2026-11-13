package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.name.mysticmonstrosityfixes.flight.CreativeFlightGrants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Remembers the flight the server just sent. Later client ticks put that choice back
 * when another mod clears it because jump is not held.
 */
@Mixin(ClientPacketListener.class)
public class CreativeFlightAbilitiesMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "handlePlayerAbilities", at = @At("TAIL"))
    private void mysticmonstrosityfixes$rememberServerFlight(ClientboundPlayerAbilitiesPacket packet, CallbackInfo ci) {
        Player player = this.minecraft.player;
        if (player == null) {
            return;
        }
        Abilities abilities = player.getAbilities();
        CreativeFlightGrants.rememberClient(player, abilities.mayfly, abilities.flying);
    }
}
