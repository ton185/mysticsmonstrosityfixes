package com.name.mysticmonstrosityfixes.mixin.experimental.blockatlas;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.name.mysticmonstrosityfixes.BlockAtlasPages;
import net.minecraft.client.renderer.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Terrain particles sample the block atlas. After the atlas becomes a texture array, they use a
 * particle shader that reads the page stored in the U coordinate.
 */
@Mixin(targets = "net.minecraft.client.particle.ParticleRenderType$1")
public class BlockAtlasParticleMixin {
    @Inject(method = "begin", at = @At("HEAD"))
    private void mysticmonstrosityfixes$pagedParticleShader(BufferBuilder builder, TextureManager textureManager, CallbackInfo callback) {
        if (BlockAtlasPages.hasArray()) {
            RenderSystem.setShader(BlockAtlasPages::terrainParticleShader);
        }
    }
}
