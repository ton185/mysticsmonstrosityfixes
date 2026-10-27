package com.name.mysticmonstrosityfixes.mixin.experimental.blockatlas;

import com.name.mysticmonstrosityfixes.BlockAtlasPages;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Replaces the block-atlas stitch with fixed-size pages. Other atlases keep the vanilla path.
 */
@Mixin(SpriteLoader.class)
public class BlockAtlasStitchMixin {
    @Shadow
    @Final
    private ResourceLocation location;

    @Shadow
    @Final
    private int maxSupportedTextureSize;

    @Inject(method = "stitch", at = @At("HEAD"), cancellable = true)
    private void mysticmonstrosityfixes$pageBlockAtlas(List<SpriteContents> sprites, int mipLevel, Executor executor,
                                                        CallbackInfoReturnable<SpriteLoader.Preparations> callback) {
        if (!BlockAtlasPages.isBlockAtlas(this.location)) {
            return;
        }
        if (BlockAtlasPages.shaderPackActive()) {
            BlockAtlasPages.skipPagingForShaderPack();
            return;
        }
        callback.setReturnValue(BlockAtlasPages.stitch(this.location, this.maxSupportedTextureSize, sprites, mipLevel, executor));
    }
}
