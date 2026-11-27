package com.name.mysticmonstrosityfixes.mixin.fixes;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(TextureAtlas.class)
public interface AtlasTextureMipAccess {
    @Accessor("width")
    int mmf$width();

    @Accessor("height")
    int mmf$height();

    @Accessor("mipLevel")
    int mmf$mipLevel();

    @Accessor("texturesByName")
    Map<ResourceLocation, TextureAtlasSprite> mmf$texturesByName();
}
