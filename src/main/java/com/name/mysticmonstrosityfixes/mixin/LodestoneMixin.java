package com.name.mysticmonstrosityfixes.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import team.lodestar.lodestone.handlers.RenderHandler;

@Mixin(value = RenderHandler.class, remap = false)
public class LodestoneMixin {

    @Inject(
            method = "copyDepthBuffer",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void skipDepthCopy(RenderTarget target, CallbackInfo ci) {
        if(ModList.get().isLoaded("immersive_portals")) {
            if (PortalRendering.isRendering()) {
                ci.cancel();
            }
        }
    }
}