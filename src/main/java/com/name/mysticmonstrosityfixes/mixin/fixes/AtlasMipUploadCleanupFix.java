package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.name.mysticmonstrosityfixes.atlasviewer.AtlasMipLevels;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureAtlas.class)
public abstract class AtlasMipUploadCleanupFix {
    @Inject(method = "upload", at = @At("HEAD"))
    private void mmf$forgetGeneratedMips(SpriteLoader.Preparations preparations, CallbackInfo ci) {
        AtlasMipLevels.onAtlasUpload((TextureAtlas) (Object) this);
    }
}
