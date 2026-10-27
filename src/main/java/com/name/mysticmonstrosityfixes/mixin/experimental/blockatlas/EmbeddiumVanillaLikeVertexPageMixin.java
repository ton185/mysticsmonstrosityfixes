package com.name.mysticmonstrosityfixes.mixin.experimental.blockatlas;

import com.name.mysticmonstrosityfixes.BlockAtlasPages;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The vanilla-like chunk format stores a float UV. The page stays in that float, and the chunk shader
 * selects the array layer with {@code floor}. {@code encodeTexture} would otherwise clamp every U to
 * just under 1, which samples page 0. The clamp redirect is optional so a replacement encoder that
 * does not call {@code Math.min} still leaves this mixin applied.
 */
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.vertex.format.impl.VanillaLikeChunkVertex", remap = false)
public class EmbeddiumVanillaLikeVertexPageMixin {
    @ModifyVariable(method = "encodeTexture", at = @At("HEAD"), argsOnly = true, remap = false)
    private static float mysticmonstrosityfixes$keepPage(float uv) {
        return BlockAtlasPages.chunkFloatUv(uv);
    }

    @Redirect(
            method = "encodeTexture",
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(FF)F"),
            require = 0,
            remap = false
    )
    private static float mysticmonstrosityfixes$keepPagedU(float a, float b) {
        return BlockAtlasPages.keepPagedFloat(a, b);
    }

    @Inject(method = "encodeTexture", at = @At("RETURN"), cancellable = true, remap = false)
    private static void mysticmonstrosityfixes$restorePagedU(float uv, CallbackInfoReturnable<Float> callback) {
        callback.setReturnValue(BlockAtlasPages.keepEncodedPage(uv, callback.getReturnValue()));
    }
}
