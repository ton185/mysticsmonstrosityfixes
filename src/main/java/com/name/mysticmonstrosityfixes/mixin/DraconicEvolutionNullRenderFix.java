package com.name.mysticmonstrosityfixes.mixin;

import codechicken.lib.render.CCModel;
import com.brandon3055.draconicevolution.client.render.tile.RenderTileReactorCore;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderTileReactorCore.class)
public class DraconicEvolutionNullRenderFix {
    @Shadow private static CCModel model;

    @Inject(at = @At("HEAD"), method = "renderCore", cancellable = true, remap = false)
    private static void nullCheck(codechicken.lib.vec.Matrix4 mat, codechicken.lib.render.CCRenderState ccrs, float animation, double animState, float intensity, float shieldPower, float partialTicks, MultiBufferSource getter, CallbackInfo ci) {
        if (model == null) {
            ci.cancel();
        }
    }
}
