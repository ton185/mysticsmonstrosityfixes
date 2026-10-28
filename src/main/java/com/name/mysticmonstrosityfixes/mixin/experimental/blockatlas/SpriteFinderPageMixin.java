package com.name.mysticmonstrosityfixes.mixin.experimental.blockatlas;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The finder quadtree covers U from 0 to 1. A sprite on a later block-atlas page reports U at or above 1,
 * and every one of those sprites falls in the same outer cell. Two of them split that cell forever.
 */
@Pseudo
@Mixin(targets = {
        "dev.technici4n.moderndynamics.thirdparty.fabric.SpriteFinderImpl$Node",
        "appeng.thirdparty.fabric.SpriteFinderImpl$Node"
}, remap = false)
public class SpriteFinderPageMixin {
    @Inject(method = "add", at = @At("HEAD"), cancellable = true, remap = false)
    private void mysticmonstrosityfixes$skipLaterPage(TextureAtlasSprite sprite, CallbackInfo callback) {
        if (sprite.getU0() >= 1.0F) {
            callback.cancel();
        }
    }
}
