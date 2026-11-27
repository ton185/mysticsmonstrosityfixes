package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.name.mysticmonstrosityfixes.atlasviewer.AtlasMipLevels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xfacthd.atlasviewer.client.screen.AtlasScreen;
import xfacthd.atlasviewer.client.screen.SpriteInfoScreen;
import xfacthd.atlasviewer.client.screen.widget.DiscreteSliderButton;

@Mixin(value = AtlasScreen.class, remap = false)
public abstract class AtlasViewerMipLevelsFix {
    @Shadow
    private TextureAtlas currentAtlas;

    @Shadow
    private DiscreteSliderButton mipLevelSlider;

    @Shadow
    private int currentMipLevel;

    @Inject(method = "selectAtlas", at = @At("RETURN"))
    private void mmf$extendMipSlider(CallbackInfo ci) {
        TextureAtlas atlas = this.currentAtlas;
        if (atlas == null || this.mipLevelSlider == null) {
            return;
        }
        AtlasTextureMipAccess access = (AtlasTextureMipAccess) (Object) atlas;
        int max = AtlasMipLevels.atlasMipLimit(access.mmf$texturesByName().values(), access.mmf$mipLevel());
        this.currentMipLevel = 0;
        this.mipLevelSlider.setMaxStep(max);
        this.mipLevelSlider.setStep(0, true);
        ((AbstractWidget) this.mipLevelSlider).active = max > 0;
        AtlasMipLevels.onAtlasSelected(this, atlas);
    }

    @Inject(method = "selectMipLevel", at = @At("HEAD"))
    private void mmf$dropMipsOnZero(int level, CallbackInfo ci) {
        if (level <= 0) {
            AtlasMipLevels.onLevelZero(this);
        }
    }

    @Inject(method = "setAtlasMipLevel", at = @At("HEAD"))
    private static void mmf$prepareMips(TextureAtlas atlas, int level, CallbackInfo ci) {
        if (atlas == null) {
            return;
        }
        AtlasTextureMipAccess access = (AtlasTextureMipAccess) (Object) atlas;
        Object screen = Minecraft.getInstance().screen;
        AtlasMipLevels.prepare(
                atlas,
                level,
                access.mmf$width(),
                access.mmf$height(),
                access.mmf$mipLevel(),
                screen instanceof AtlasScreen || screen instanceof SpriteInfoScreen,
                screen,
                access.mmf$texturesByName()
        );
    }

    public void removed() {
        AtlasMipLevels.onViewerClosed();
    }

    public void m_7861_() {
        AtlasMipLevels.onViewerClosed();
    }
}
