package com.name.mysticmonstrosityfixes.mixin.fixes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * GUI Scale Additions remembers the fine scale only when {@code setGuiScale} is handed the raw
 * auto scale. Slate's compact scale passes that value minus one, so the remembered value stays 0
 * while {@code guiScaleFine} is 30. Every mouse release then calls {@code resizeDisplay}, the open
 * screen is built again, and the option or text box that was just clicked is gone.
 */
@Pseudo
@Mixin(targets = "com.misanthropy.gui_scale_additions.Client", remap = false)
public class GuiScaleClickFix {
    @Shadow(remap = false)
    public static OptionInstance<Integer> FINE;

    @Shadow(remap = false)
    private static int applied;

    @Inject(method = "scale", at = @At("HEAD"), cancellable = true, remap = false)
    private static void mysticmonstrosityfixes$keepFineScale(Minecraft minecraft, double incoming, CallbackInfoReturnable<Double> cir) {
        int guiScale = minecraft.options.guiScale().get();
        int vanilla = minecraft.getWindow().calculateScale(guiScale, minecraft.isEnforceUnicode());
        int compact = guiScale == 0 && vanilla >= 3 ? vanilla - 1 : vanilla;
        if (incoming != vanilla && incoming != compact) {
            return;
        }
        int fine = FINE.get();
        applied = fine;
        if (fine == 0) {
            cir.setReturnValue(incoming);
        } else {
            cir.setReturnValue(Math.max(fine, 5) / 10.0);
        }
    }
}
