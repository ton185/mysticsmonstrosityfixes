package com.name.mysticmonstrosityfixes.mixin.experimental.blockatlas;

import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.Program;
import com.name.mysticmonstrosityfixes.BlockAtlasPages;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;
import java.util.List;

/**
 * Patches block-atlas fragment shaders after their imports expand, so a shader added later
 * still samples the page stored in the U coordinate. An active Iris shader pack keeps the 2D sample.
 * The processed line list is changed on the way into glShaderSource, leaving GlslPreprocessor.process
 * free for Oculus to redirect.
 */
@Mixin(Program.class)
public class BlockAtlasShaderSourceMixin {
    @Inject(method = "compileShaderInternal", at = @At("HEAD"))
    private static void mysticmonstrosityfixes$captureShader(Program.Type type, String name, InputStream stream, String id,
                                                             GlslPreprocessor preprocessor, CallbackInfoReturnable<Integer> callback) {
        BlockAtlasPages.beginCompile(type, name, preprocessor);
    }

    @ModifyArg(
            method = "compileShaderInternal",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/platform/GlStateManager;glShaderSource(ILjava/util/List;)V"
            ),
            index = 1
    )
    private static List<String> mysticmonstrosityfixes$pagedBlockShaders(List<String> processed) {
        try {
            return BlockAtlasPages.rewriteProcessed(processed);
        } finally {
            BlockAtlasPages.endCompile();
        }
    }
}
