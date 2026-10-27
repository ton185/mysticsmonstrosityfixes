package com.name.mysticmonstrosityfixes.mixin.experimental.blockatlas;

import com.name.mysticmonstrosityfixes.BlockAtlasPages;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderLoader;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Embeddium's chunk shader samples the block atlas as one 2D texture. Point it at the page array.
 * The vanilla-like format carries the page in U. The compact format still carries it in the material byte.
 */
@Mixin(value = ShaderLoader.class, remap = false)
public class EmbeddiumShaderPageMixin {
    @Redirect(
            method = "loadShader",
            at = @At(
                    value = "INVOKE",
                    target = "Lme/jellysquid/mods/sodium/client/gl/shader/ShaderLoader;getShaderSource(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/String;"
            ),
            remap = false
    )
    private static String mysticmonstrosityfixes$pagedChunkShader(ResourceLocation location) {
        return BlockAtlasPages.rewriteSodiumShader(location, ShaderLoader.getShaderSource(location));
    }
}
