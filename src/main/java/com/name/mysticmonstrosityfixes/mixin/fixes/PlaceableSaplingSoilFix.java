package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.name.mysticmonstrosityfixes.placeable.PlaceableBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Placeable injects {@code advanceTree} by its official name. The refmap that would rename it to
 * {@code m_222000_} is {@code mixin.refmap.json}, and another mod's copy of that resource can be
 * the one the classloader returns. The extra method keeps that required inject from aborting the
 * game. Callers still enter the SRG method, so the soil check is applied there.
 */
@Mixin(SaplingBlock.class)
public class PlaceableSaplingSoilFix {
    @SuppressWarnings("unused")
    @Unique
    private void advanceTree(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
    }

    @Inject(method = "advanceTree", at = @At("HEAD"), cancellable = true)
    private void mysticmonstrosityfixes$requireSoil(ServerLevel level, BlockPos pos, BlockState state, RandomSource random, CallbackInfo ci) {
        if (!PlaceableBridge.loaded()) {
            return;
        }
        BlockState floor = level.getBlockState(pos.below());
        if (!floor.is(BlockTags.DIRT) && !floor.is(Blocks.FARMLAND)) {
            ci.cancel();
        }
    }
}
