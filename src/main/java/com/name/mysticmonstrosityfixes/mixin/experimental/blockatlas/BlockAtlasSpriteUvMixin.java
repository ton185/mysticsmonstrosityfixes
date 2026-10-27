package com.name.mysticmonstrosityfixes.mixin.experimental.blockatlas;

import com.name.mysticmonstrosityfixes.BlockAtlasPages;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shifts block-sprite U coordinates onto their atlas page. V stays inside the page.
 * The integer part of U is the page, and the fraction is the position inside that page.
 */
@Mixin(TextureAtlasSprite.class)
public class BlockAtlasSpriteUvMixin {
    @Inject(method = "getU0", at = @At("RETURN"), cancellable = true)
    private void mysticmonstrosityfixes$pageU0(CallbackInfoReturnable<Float> callback) {
        callback.setReturnValue(BlockAtlasPages.pagedUv((TextureAtlasSprite) (Object) this, callback.getReturnValue()));
    }

    @Inject(method = "getU1", at = @At("RETURN"), cancellable = true)
    private void mysticmonstrosityfixes$pageU1(CallbackInfoReturnable<Float> callback) {
        callback.setReturnValue(BlockAtlasPages.pagedUv((TextureAtlasSprite) (Object) this, callback.getReturnValue()));
    }

    @Inject(method = "getU", at = @At("RETURN"), cancellable = true)
    private void mysticmonstrosityfixes$pageU(double x, CallbackInfoReturnable<Float> callback) {
        callback.setReturnValue(BlockAtlasPages.pagedUv((TextureAtlasSprite) (Object) this, callback.getReturnValue()));
    }
}
