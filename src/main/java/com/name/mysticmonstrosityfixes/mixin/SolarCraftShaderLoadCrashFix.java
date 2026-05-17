package com.name.mysticmonstrosityfixes.mixin;

import com.finderfeed.solarcraft.client.rendering.shaders.post_chains.PostChainPlusUltra;
import com.finderfeed.solarcraft.content.blocks.render.RuneEnergyPylonRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RuneEnergyPylonRenderer.class)
public class SolarCraftShaderLoadCrashFix {
    @Redirect(at = @At(value = "INVOKE", target = "Lcom/finderfeed/solarcraft/client/rendering/shaders/post_chains/PostChainPlusUltra;resize(II)V"), method = "loadShader")
    public void dontResize(PostChainPlusUltra instance, int i, int i2) {
        // no-op the resize
        return;
    }
}
