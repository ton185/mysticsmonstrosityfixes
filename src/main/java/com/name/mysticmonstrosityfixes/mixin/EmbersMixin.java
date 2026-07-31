package com.name.mysticmonstrosityfixes.mixin;

import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.rekindled.embers.EmbersClientEvents;

import qouteall.imm_ptl.core.render.context_management.PortalRendering;

@Mixin(value = EmbersClientEvents.class, remap = false)
public class EmbersMixin {

    @Inject(
            method = "onWorldRender",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void skipDuringPortalRendering(RenderLevelStageEvent event, CallbackInfo ci) {
        if (PortalRendering.isRendering()) {
            ci.cancel();
        }
    }
}