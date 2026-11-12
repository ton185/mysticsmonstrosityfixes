package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.name.mysticmonstrosityfixes.flight.CreativeFlightGrants;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs after the player tick that other mods use to clear creative flight.
 * {@code doTick} is the tick that actually calls {@code Player.tick}.
 * Priority 0 is applied after the usual mixins, so this tail runs last.
 */
@Mixin(value = ServerPlayer.class, priority = 0)
public class CreativeFlightGrantMixin {
    @Inject(method = {"tick", "doTick"}, at = @At("TAIL"))
    private void mysticmonstrosityfixes$keepCreativeFlight(CallbackInfo ci) {
        CreativeFlightGrants.keep((ServerPlayer) (Object) this);
    }
}
