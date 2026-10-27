package com.name.mysticmonstrosityfixes.mixin.experimental.blockatlas;

import com.name.mysticmonstrosityfixes.BlockAtlasPages;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.Material;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Compact vertices store a 16-bit UV, so the page cannot ride in U past the first extra page.
 * It is stamped into the material byte as that byte is stored. The vanilla-like format does not use
 * this path. While no shader pack is active, chunk meshes are forced onto that float format.
 */
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.vertex.format.impl.CompactChunkVertex", remap = false)
public class EmbeddiumCompactVertexPageMixin {
    @Inject(method = "lambda$getEncoder$0", at = @At("HEAD"), remap = false)
    private static void mysticmonstrosityfixes$capturePage(long ptr, Material material, ChunkVertexEncoder.Vertex vertex,
                                                           int section, CallbackInfoReturnable<Long> callback) {
        BlockAtlasPages.beginCompactVertex();
        BlockAtlasPages.captureUv(vertex.u);
    }

    @Redirect(
            method = "lambda$getEncoder$0",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/system/MemoryUtil;memPutByte(JB)V"),
            remap = false
    )
    private static void mysticmonstrosityfixes$stampPage(long address, byte value) {
        if (BlockAtlasPages.compactMaterialByte()) {
            value = (byte) BlockAtlasPages.materialBitsWithPage(value & 0xFF);
        }
        MemoryUtil.memPutByte(address, value);
    }

    @ModifyVariable(method = "encodeTexture", at = @At("HEAD"), argsOnly = true, remap = false)
    private static float mysticmonstrosityfixes$localUv(float uv) {
        return BlockAtlasPages.chunkLocalUv(uv);
    }
}
