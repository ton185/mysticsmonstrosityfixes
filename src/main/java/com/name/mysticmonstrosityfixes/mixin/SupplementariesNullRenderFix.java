package com.name.mysticmonstrosityfixes.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.mehvahdjukaar.supplementaries.client.renderers.tiles.BookPileBlockTileRenderer;
import net.mehvahdjukaar.supplementaries.common.block.tiles.BookPileBlockTile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

@Mixin(BookPileBlockTileRenderer.class)
public class SupplementariesNullRenderFix {
    @Shadow(remap = false) private static ModelBlockRenderer renderer;

    @Inject(at = @At("HEAD"), method = "renderBook(Lcom/mojang/blaze3d/vertex/PoseStack;Ljava/util/function/Function;IILnet/mehvahdjukaar/supplementaries/common/block/tiles/BookPileBlockTile$VisualBook;FF)V", remap = false)
    private static void nullCheck(PoseStack poseStack, Function<BookPileBlockTile.VisualBook, VertexConsumer> vertexBuilder, int light, int overlay, BookPileBlockTile.VisualBook b, float xRot, float zRot, CallbackInfo ci) {
        if (renderer == null) {
            renderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        }
    }
}
