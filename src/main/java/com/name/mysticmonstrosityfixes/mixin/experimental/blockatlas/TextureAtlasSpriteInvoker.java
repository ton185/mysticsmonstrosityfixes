package com.name.mysticmonstrosityfixes.mixin.experimental.blockatlas;

import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(TextureAtlasSprite.class)
public interface TextureAtlasSpriteInvoker {
    @Invoker("<init>")
    static TextureAtlasSprite mysticmonstrosityfixes$create(ResourceLocation atlas, SpriteContents contents,
                                                            int atlasWidth, int atlasHeight, int x, int y) {
        throw new AssertionError();
    }
}
