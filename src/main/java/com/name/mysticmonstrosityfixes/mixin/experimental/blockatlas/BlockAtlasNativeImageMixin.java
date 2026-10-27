package com.name.mysticmonstrosityfixes.mixin.experimental.blockatlas;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.name.mysticmonstrosityfixes.BlockAtlasPages;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Sprite uploads during a paged atlas stitch write into the current page's array layer.
 */
@Mixin(NativeImage.class)
public class BlockAtlasNativeImageMixin {
    @Redirect(
            method = "_upload",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/platform/GlStateManager;_texSubImage2D(IIIIIIIIJ)V"
            )
    )
    private void mysticmonstrosityfixes$uploadLayer(int target, int level, int x, int y, int width, int height,
                                                    int format, int type, long pixels) {
        int layer = BlockAtlasPages.uploadLayer();
        if (layer < 0) {
            GlStateManager._texSubImage2D(target, level, x, y, width, height, format, type, pixels);
            return;
        }
        // A sprite upload binds the 2D atlas as well. That does not remove the array binding, but
        // rebinding here keeps a later page from writing into whatever array target was left bound.
        int array = BlockAtlasPages.arrayId();
        if (array != 0) {
            GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, array);
        }
        GL12.glTexSubImage3D(GL30.GL_TEXTURE_2D_ARRAY, level, x, y, layer, width, height, 1, format, type, pixels);
    }
}
