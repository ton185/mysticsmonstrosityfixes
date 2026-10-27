package com.name.mysticmonstrosityfixes.mixin.experimental.blockatlas;

import com.name.mysticmonstrosityfixes.BlockAtlasPages;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The page rewrite also runs on the shader text after includes are inlined. A rewrite that only
 * wraps the file read can be replaced by another mod before the program is compiled, and the chunk
 * mesh then samples page 0.
 */
@Mixin(targets = "me.jellysquid.mods.sodium.client.gl.shader.ShaderParser", remap = false)
public class EmbeddiumShaderParserMixin {
    @Inject(
            method = "parseShader(Ljava/lang/String;Lme/jellysquid/mods/sodium/client/gl/shader/ShaderConstants;)Ljava/lang/String;",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private static void mysticmonstrosityfixes$pageParsedShader(String source, ShaderConstants constants,
                                                                CallbackInfoReturnable<String> callback) {
        String rewritten = BlockAtlasPages.rewriteParsedSodiumShader(callback.getReturnValue());
        if (rewritten != null && !rewritten.equals(callback.getReturnValue())) {
            callback.setReturnValue(rewritten);
        }
    }
}
