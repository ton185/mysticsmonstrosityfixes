package com.name.mysticmonstrosityfixes.mixin.experimental.blockatlas;

import com.mojang.blaze3d.systems.RenderSystem;
import com.name.mysticmonstrosityfixes.BlockAtlasPages;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Block shaders sample the page array. Entity and item shaders keep Sampler0 on the page-0 view
 * and bind the page array on a separate sampler after the program is in use.
 */
@Mixin(ShaderInstance.class)
public class BlockAtlasShaderBindMixin {
    @Redirect(
            method = "apply",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;bindTexture(I)V")
    )
    private void mysticmonstrosityfixes$bindPagedSampler(int texture) {
        RenderSystem.bindTexture(BlockAtlasPages.samplerId((ShaderInstance) (Object) this, texture));
    }

    @Inject(method = "apply", at = @At("RETURN"))
    private void mysticmonstrosityfixes$bindSharedPages(CallbackInfo callback) {
        BlockAtlasPages.bindSharedAtlas((ShaderInstance) (Object) this);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void mysticmonstrosityfixes$noteSharedProgram(CallbackInfo callback) {
        BlockAtlasPages.noteSharedProgram((ShaderInstance) (Object) this);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void mysticmonstrosityfixes$forgetSharedProgram(CallbackInfo callback) {
        BlockAtlasPages.forgetSharedProgram((ShaderInstance) (Object) this);
    }
}
