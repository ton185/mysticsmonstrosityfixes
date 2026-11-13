package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.name.mysticmonstrosityfixes.flight.CreativeFlightGrants;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Granted flight was only written back at the end of the player tick, after movement.
 * Gravity, and mods that add their own downward speed when jump is up, then pull the player down.
 * Vanilla creative flight keeps the pre-move vertical speed at six tenths and does not sink.
 * Priority 0 is applied after the usual mixins, so the tail runs last.
 */
@Mixin(value = Player.class, priority = 0)
public class CreativeFlightHoverMixin {
    private double mysticmonstrosityfixes$flightY;
    private boolean mysticmonstrosityfixes$hover;

    @Inject(method = "travel", at = @At("HEAD"))
    private void mysticmonstrosityfixes$holdFlight(Vec3 travel, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        CreativeFlightGrants.restoreHover(player);
        this.mysticmonstrosityfixes$hover = CreativeFlightGrants.hover(player);
        this.mysticmonstrosityfixes$flightY = player.getDeltaMovement().y;
    }

    @Inject(method = "travel", at = @At("TAIL"))
    private void mysticmonstrosityfixes$keepFlightHeight(Vec3 travel, CallbackInfo ci) {
        if (!this.mysticmonstrosityfixes$hover) {
            return;
        }
        Player player = (Player) (Object) this;
        Vec3 motion = player.getDeltaMovement();
        double hoverY = this.mysticmonstrosityfixes$flightY * 0.6D;
        if (motion.y < hoverY) {
            player.setDeltaMovement(motion.x, hoverY, motion.z);
            player.resetFallDistance();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void mysticmonstrosityfixes$stopFlightSink(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (!CreativeFlightGrants.hover(player) || player.isShiftKeyDown() || player.onGround()) {
            return;
        }
        Vec3 motion = player.getDeltaMovement();
        if (motion.y < 0.0D) {
            player.setDeltaMovement(motion.x, 0.0D, motion.z);
            player.resetFallDistance();
        }
    }
}
