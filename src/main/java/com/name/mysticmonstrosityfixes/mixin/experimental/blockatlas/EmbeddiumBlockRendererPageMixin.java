package com.name.mysticmonstrosityfixes.mixin.experimental.blockatlas;

import com.name.mysticmonstrosityfixes.BlockAtlasPages;
import me.jellysquid.mods.sodium.client.model.quad.BakedQuadView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Chunk meshes read the U stored on the baked quad. That value is often still inside one page.
 * The sprite knows which page its pixels were uploaded to, so the U written into the mesh does too.
 */
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap = false)
public class EmbeddiumBlockRendererPageMixin {
    @Redirect(
            method = "writeGeometry",
            at = @At(
                    value = "INVOKE",
                    target = "Lme/jellysquid/mods/sodium/client/model/quad/BakedQuadView;getTexU(I)F"
            ),
            remap = false
    )
    private float mysticmonstrosityfixes$pageU(BakedQuadView quad, int vertex) {
        return BlockAtlasPages.uvOnPage(quad.getSprite(), quad.getTexU(vertex));
    }
}
