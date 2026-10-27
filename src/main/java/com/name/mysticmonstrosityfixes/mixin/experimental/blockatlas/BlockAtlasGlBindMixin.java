package com.name.mysticmonstrosityfixes.mixin.experimental.blockatlas;

import com.mojang.blaze3d.platform.GlStateManager;
import com.name.mysticmonstrosityfixes.BlockAtlasPages;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The block atlas id is a 2D array. Binds and parameter changes for that id use the array target.
 * A cached bind still records which texture the game asked for, so a later filter change for a chest
 * or a fluid does not land on the page array.
 */
@Mixin(GlStateManager.class)
public class BlockAtlasGlBindMixin {
    @Inject(method = "_bindTexture", at = @At("HEAD"))
    private static void mysticmonstrosityfixes$noteBind(int texture, CallbackInfo callback) {
        BlockAtlasPages.noteGlBind(texture);
    }

    @Redirect(
            method = "_bindTexture",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glBindTexture(II)V", remap = false)
    )
    private static void mysticmonstrosityfixes$bindArray(int target, int texture) {
        BlockAtlasPages.bindTexture(target, texture);
    }

    @Redirect(
            method = "_texParameter(IIF)V",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTexParameterf(IIF)V", remap = false)
    )
    private static void mysticmonstrosityfixes$parameterF(int target, int name, float value) {
        GL11.glTexParameterf(BlockAtlasPages.parameterTarget(target), name, value);
    }

    @Redirect(
            method = "_texParameter(III)V",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTexParameteri(III)V", remap = false)
    )
    private static void mysticmonstrosityfixes$parameterI(int target, int name, int value) {
        GL11.glTexParameteri(BlockAtlasPages.parameterTarget(target), name, value);
    }
}
