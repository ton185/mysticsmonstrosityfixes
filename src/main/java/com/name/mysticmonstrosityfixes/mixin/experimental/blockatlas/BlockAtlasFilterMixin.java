package com.name.mysticmonstrosityfixes.mixin.experimental.blockatlas;

import com.name.mysticmonstrosityfixes.BlockAtlasPages;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the page array on the same filter as the 2D page view the atlas id points at.
 * The array is updated by its own id, so this call does not steal the filter from a chest,
 * fluid, gas, or item texture that was bound a moment earlier.
 */
@Mixin(AbstractTexture.class)
public abstract class BlockAtlasFilterMixin {
    @Shadow
    protected int id;

    @Inject(method = "setFilter", at = @At("RETURN"))
    private void mysticmonstrosityfixes$arrayFilter(boolean blur, boolean mipmap, CallbackInfo callback) {
        BlockAtlasPages.filterArrayIfAtlas(this.id, blur, mipmap);
    }
}
