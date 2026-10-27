package com.name.mysticmonstrosityfixes.mixin.experimental.blockatlas;

import com.name.mysticmonstrosityfixes.BlockAtlasPages;
import net.minecraft.client.renderer.texture.Stitcher;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A sprite that opens the next page is expected. Skip the vanilla "unable to fit" dump while paging.
 * ModernFix logs the same overflow from its own stitcher. {@code AtlasOverflowLogFilter} drops that line.
 */
@Mixin(value = Stitcher.class, priority = 500)
public class BlockAtlasStitcherMixin {
    @Redirect(method = "stitch", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;isInfoEnabled()Z", remap = false))
    private boolean mysticmonstrosityfixes$quietPageOverflow(Logger logger) {
        if (BlockAtlasPages.isPaging()) {
            return false;
        }
        return logger.isInfoEnabled();
    }
}
