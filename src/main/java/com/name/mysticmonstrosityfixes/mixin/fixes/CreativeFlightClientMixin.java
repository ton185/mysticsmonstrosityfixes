package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.name.mysticmonstrosityfixes.flight.CreativeFlightGrants;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Abilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The jump boost and the flight toggle both read {@code flying} before {@code travel}.
 * Putting the flag back here means holding jump still climbs, and letting go hovers.
 */
@Mixin(value = LocalPlayer.class, priority = 0)
public class CreativeFlightClientMixin {
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void mysticmonstrosityfixes$holdFlightBeforeInput(CallbackInfo ci) {
        CreativeFlightGrants.restoreHover((LocalPlayer) (Object) this);
    }

    @Inject(method = "onUpdateAbilities", at = @At("HEAD"))
    private void mysticmonstrosityfixes$rememberLocalFlight(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        Abilities abilities = player.getAbilities();
        CreativeFlightGrants.rememberClient(player, abilities.mayfly, abilities.flying);
    }
}
