package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.name.mysticmonstrosityfixes.vram_usage.VramUsage;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Adds a VRAM usage line directly below the two heap allocation lines on the F3 debug screen.
 * The line is skipped when no VRAM source works instead of showing an error or a bogus value.
 */
@Mixin(DebugScreenOverlay.class)
public class DebugScreenVramLineFix {

    @Inject(method = "getSystemInformation", at = @At("RETURN"))
    private void mysticmonstrosityfixes$addVramLine(CallbackInfoReturnable<List<String>> cir) {
        try {
            List<String> lines = cir.getReturnValue();
            if (lines == null) {
                return;
            }
            String line = VramUsage.debugLine();
            if (line == null) {
                return;
            }
            int insertAt = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).startsWith("Allocated:")) {
                    insertAt = i + 1;
                    break;
                }
            }
            if (insertAt < 0) {
                return;
            }
            if (insertAt < lines.size() && lines.get(insertAt).startsWith("VRAM:")) {
                return;
            }
            lines.add(insertAt, line);
        } catch (Throwable ignored) {
        }
    }
}
