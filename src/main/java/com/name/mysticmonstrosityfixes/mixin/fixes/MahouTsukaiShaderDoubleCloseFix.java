package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.mojang.blaze3d.shaders.Uniform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mahou Tsukai closes its custom uniforms before calling ShaderInstance.close(), which closes
 * every uniform again. The second close frees an already-freed native buffer, corrupting jemalloc
 * and crashing unrelated native allocations later. Let super.close() handle all uniforms.
 */
@Mixin(targets = "stepsword.mahoutsukai.render.shader.ClipShaderInstance", remap = false)
public abstract class MahouTsukaiShaderDoubleCloseFix {
    @Redirect(
            method = "close",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/shaders/Uniform;close()V"),
            remap = false)
    private void mysticmonstrosityfixes$skipExplicitUniformClose(Uniform uniform) {
    }
}
