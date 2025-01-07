package com.name.mysticmonstrosityfixes.mixin;

import codechicken.lib.render.CCModel;
import codechicken.lib.render.model.OBJParser;
import com.brandon3055.draconicevolution.DraconicEvolution;
import com.brandon3055.draconicevolution.client.render.tile.RenderTileReactorCore;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(RenderTileReactorCore.class)
public class DraconicEvolutionNullRenderFix {
    @Shadow
    private static CCModel model;

    @Inject(at = @At("HEAD"), method = "renderCore", cancellable = true, remap = false)
    private static void nullCheck(codechicken.lib.vec.Matrix4 mat, codechicken.lib.render.CCRenderState ccrs, float animation, double animState, float intensity, float shieldPower, float partialTicks, MultiBufferSource getter, CallbackInfo ci) {
        if (model == null) {
            Map<String, CCModel> map = new OBJParser(new ResourceLocation(DraconicEvolution.MODID, "models/block/reactor/reactor_core.obj")).quads().ignoreMtl().parse();
            model = CCModel.combine(map.values());
        }
    }
}
