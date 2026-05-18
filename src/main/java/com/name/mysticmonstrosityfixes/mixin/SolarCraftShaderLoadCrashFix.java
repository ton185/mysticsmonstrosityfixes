package com.name.mysticmonstrosityfixes.mixin;

import com.finderfeed.solarcraft.content.blocks.render.RuneEnergyPylonRenderer;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RuneEnergyPylonRenderer.class)
public class SolarCraftShaderLoadCrashFix {
    @Redirect(at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;getScreenWidth()I"), method = "loadShader")
    public int fixWidth(Window instance) {
        return instance.getWidth();
    }

    @Redirect(at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;getScreenHeight()I"), method = "loadShader")
    public int fixHeight(Window instance) {
        return instance.getHeight();
    }
}
