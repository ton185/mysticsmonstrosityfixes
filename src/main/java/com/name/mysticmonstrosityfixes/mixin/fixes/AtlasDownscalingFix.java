package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.mojang.blaze3d.platform.NativeImage;
import com.name.mysticmonstrosityfixes.AtlasDownscaling;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SpriteLoader.class)
public class AtlasDownscalingFix {

    @Redirect(
            method = "loadSprite",
            at = @At(value = "NEW", target = "net/minecraft/client/renderer/texture/SpriteContents")
    )
    private static SpriteContents mmf$downscaleSprite(
            ResourceLocation name, FrameSize frameSize, NativeImage image, AnimationMetadataSection animation) {
        return AtlasDownscaling.create(name, frameSize, image, animation);
    }
}
