package com.name.mysticmonstrosityfixes.mixin.experimental.blockatlas;

import com.name.mysticmonstrosityfixes.BlockAtlasPages;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Compact chunk vertices pack UV into 16 bits, which cannot hold a page of 2 or more. While no Iris
 * shader pack is active, meshes and chunk shaders use the vanilla-like float UV so the page stays in U.
 * A shader pack skips paging, and this leaves Embeddium's own choice in place. Priority is below the
 * default so a return inject from another mod is overwritten by this one.
 */
@Mixin(targets = "me.jellysquid.mods.sodium.client.SodiumClientMod", priority = 0, remap = false)
public class EmbeddiumVanillaVertexFormatMixin {
    @Inject(method = "canUseVanillaVertices", at = @At("RETURN"), cancellable = true, remap = false)
    private static void mysticmonstrosityfixes$floatPageUv(CallbackInfoReturnable<Boolean> callback) {
        if (!BlockAtlasPages.shaderPackActive()) {
            callback.setReturnValue(Boolean.TRUE);
        }
    }
}
