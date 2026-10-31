package com.name.mysticmonstrosityfixes.mixin.loading;

import dev.fallingcloud.slate.core.stage.StageRenderContext;
import dev.fallingcloud.slate.core.stage.node.TerrainNode;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TerrainNode.ensureMesh rebuilds its quad meshes whenever StageResources.generation() changes.
 * While the loading overlay is up and dynamic resources are on, every lazily baked model bumps
 * that generation, so the terrain re-tessellates and re-bakes every frame. Skip drawing the node
 * while an overlay covers the screen; the first frame after loading builds it once.
 */
@Mixin(value = TerrainNode.class, remap = false)
public class SlateTerrainLoadFix {
    @Inject(method = {"draw", "drawTranslucent"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void mysticmonstrosityfixes$skipWhileLoading(StageRenderContext context, CallbackInfo ci) {
        if (Minecraft.getInstance().getOverlay() != null) {
            ci.cancel();
        }
    }
}
