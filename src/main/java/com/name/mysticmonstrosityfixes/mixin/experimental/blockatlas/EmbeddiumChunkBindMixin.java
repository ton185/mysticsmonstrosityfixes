package com.name.mysticmonstrosityfixes.mixin.experimental.blockatlas;

import com.name.mysticmonstrosityfixes.BlockAtlasPages;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Emberbium binds the block atlas itself while drawing chunks. That bind samples the page array.
 */
@Mixin(value = ChunkShaderInterface.class, remap = false)
public class EmbeddiumChunkBindMixin {
    @Redirect(
            method = "bindTexture",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_bindTexture(I)V"),
            remap = true
    )
    private void mysticmonstrosityfixes$bindChunkAtlas(int texture) {
        BlockAtlasPages.bindChunkTexture(texture);
    }
}
