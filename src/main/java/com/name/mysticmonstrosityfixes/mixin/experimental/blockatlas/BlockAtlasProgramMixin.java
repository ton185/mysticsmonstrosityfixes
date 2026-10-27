package com.name.mysticmonstrosityfixes.mixin.experimental.blockatlas;

import com.mojang.blaze3d.shaders.ProgramManager;
import com.name.mysticmonstrosityfixes.BlockAtlasPages;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Some mods call {@code glUseProgram} on a shader that never goes through {@code ShaderInstance.apply}.
 * The page array still has to be bound while that program is current.
 */
@Mixin(ProgramManager.class)
public class BlockAtlasProgramMixin {
    @Inject(method = "glUseProgram", at = @At("RETURN"))
    private static void mysticmonstrosityfixes$bindSharedPages(int program, CallbackInfo callback) {
        BlockAtlasPages.bindSharedProgram(program);
    }
}
