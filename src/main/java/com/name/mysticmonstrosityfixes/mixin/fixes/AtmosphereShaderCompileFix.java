package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.mojang.blaze3d.shaders.Program;
import org.apache.commons.io.IOUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.regex.Pattern;

/**
 * Let There Be Atmosphere names a vertex-shader local {@code packed}. That word is reserved in
 * GLSL 1.50, and this machine's driver rejects the nine shaders that use it. EmberLens then calls
 * {@code cancel()} on the non-cancellable compile inject, so the log says
 * "The call m_166612_ is not cancellable" instead of the shader log.
 */
@Mixin(Program.class)
public class AtmosphereShaderCompileFix {
    private static final Pattern PACKED_IDENTIFIER = Pattern.compile("(?<![A-Za-z0-9_])packed(?![A-Za-z0-9_])");

    @Redirect(
            method = "compileShaderInternal",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/apache/commons/io/IOUtils;toString(Ljava/io/InputStream;Ljava/nio/charset/Charset;)Ljava/lang/String;",
                    remap = false
            )
    )
    private static String mysticmonstrosityfixes$renameReservedPacked(InputStream input, Charset charset) throws IOException {
        String source = IOUtils.toString(input, charset);
        if (source == null || source.indexOf("packed") < 0) {
            return source;
        }
        return PACKED_IDENTIFIER.matcher(source).replaceAll("mmf_packed");
    }
}
