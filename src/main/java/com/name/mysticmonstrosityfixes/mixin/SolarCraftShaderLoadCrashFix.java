package com.name.mysticmonstrosityfixes.mixin;

import com.finderfeed.solarcraft.client.rendering.shaders.post_chains.UniformPlusPlus;
import com.finderfeed.solarcraft.content.blocks.render.RuneEnergyPylonRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RuneEnergyPylonRenderer.class)
public class SolarCraftShaderLoadCrashFix {
    @Inject(at = @At(value = "INVOKE", target = "Ljava/lang/Exception;printStackTrace()V"), method = "loadShader", cancellable = true, remap = false)
    public void cancelIfErrored(BlockEntity tile, ResourceLocation LOC, UniformPlusPlus uniforms, CallbackInfo ci) {
        ci.cancel();
    }
}
