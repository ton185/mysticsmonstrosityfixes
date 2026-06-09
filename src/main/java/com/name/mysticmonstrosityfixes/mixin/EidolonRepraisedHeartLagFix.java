package com.name.mysticmonstrosityfixes.mixin;

import elucent.eidolon.client.ClientRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientRegistry.EidolonHearts.class)
public class EidolonRepraisedHeartLagFix {
    @Inject(at = @At("HEAD"), cancellable = true, method = "render", remap = false)
    public void dontRender(ForgeGui gui, GuiGraphics guiGraphics, float partialTicks, int width, int height, CallbackInfo ci) {
        ci.cancel();
    }
}
