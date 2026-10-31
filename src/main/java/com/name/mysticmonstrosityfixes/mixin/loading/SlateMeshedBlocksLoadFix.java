package com.name.mysticmonstrosityfixes.mixin.loading;

import dev.fallingcloud.slate.core.stage.StageRenderContext;
import dev.fallingcloud.slate.core.stage.node.MeshedBlocksNode;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * With ModernFix dynamic resources on, a Slate block mesh is rebuilt every frame while the loading
 * overlay is up: each build lazily bakes the missing models, which bumps the resource generation
 * and invalidates the mesh again, so loading never finishes. Skip drawing the node while an overlay
 * covers the screen; the first frame after loading builds the mesh once and it stays cached.
 */
@Mixin(value = MeshedBlocksNode.class, remap = false)
public class SlateMeshedBlocksLoadFix {
    @Inject(method = {"draw", "drawTranslucent"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void mysticmonstrosityfixes$skipWhileLoading(StageRenderContext context, CallbackInfo ci) {
        if (Minecraft.getInstance().getOverlay() != null) {
            ci.cancel();
        }
    }
}
