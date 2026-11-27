package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.name.mysticmonstrosityfixes.atlasviewer.AtlasMipLevels;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xfacthd.atlasviewer.client.screen.SpriteInfoScreen;

@Mixin(value = SpriteInfoScreen.class, remap = false)
public abstract class SpriteInfoScreenMipExportFix {
    @Shadow
    private SpriteContents contents;

    @ModifyVariable(method = "exportSprite(I)V", at = @At("HEAD"), argsOnly = true)
    private int mmf$clampMippedExport(int mipLevel) {
        if (this.contents == null || this.contents.byMipLevel.length == 0) {
            return 0;
        }
        return Math.max(0, Math.min(mipLevel, this.contents.byMipLevel.length - 1));
    }

    @Inject(method = "selectMipLevel", at = @At("HEAD"))
    private void mmf$dropMipsOnZero(int level, CallbackInfo ci) {
        if (level <= 0) {
            AtlasMipLevels.onLevelZero(this);
        }
    }

    public void removed() {
        AtlasMipLevels.onScreenClosed(this);
    }

    public void m_7861_() {
        AtlasMipLevels.onScreenClosed(this);
    }
}
