package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.mojang.blaze3d.shaders.Program;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Let There Be Atmosphere names a vertex-shader local {@code packed}. That word is reserved in
 * GLSL 1.50, and this machine's driver rejects the nine shaders that use it. EmberLens then calls
 * {@code cancel()} on the non-cancellable compile inject, so the log says
 * "The call m_166612_ is not cancellable" instead of the shader log.
 * <p>
 * The rename rides the line list handed to {@code glShaderSource}. A missing target must not abort
 * startup: with the block-atlas mixin off, another mixin can already own an earlier read of the
 * same shader, and a required redirect there was crashing the load.
 */
@Mixin(value = Program.class, priority = 1500)
public class AtmosphereShaderCompileFix {
    private static final Pattern PACKED_IDENTIFIER = Pattern.compile("(?<![A-Za-z0-9_])packed(?![A-Za-z0-9_])");

    @ModifyArg(
            method = "compileShaderInternal",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/platform/GlStateManager;glShaderSource(ILjava/util/List;)V"
            ),
            index = 1,
            require = 0
    )
    private static List<String> mysticmonstrosityfixes$renameReservedPacked(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return lines;
        }
        boolean hit = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line != null && line.indexOf("packed") >= 0) {
                hit = true;
                break;
            }
        }
        if (!hit) {
            return lines;
        }
        List<String> renamed = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            renamed.add(line == null || line.indexOf("packed") < 0
                    ? line
                    : PACKED_IDENTIFIER.matcher(line).replaceAll("mmf_packed"));
        }
        return renamed;
    }
}
