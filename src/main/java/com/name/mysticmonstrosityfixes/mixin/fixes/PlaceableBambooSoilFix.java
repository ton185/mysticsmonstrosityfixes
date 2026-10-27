package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.name.mysticmonstrosityfixes.placeable.PlaceableBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Same shared-refmap failure as {@link PlaceableSaplingSoilFix}, for BambooSaplingBlock.randomTick.
 */
@Mixin(BambooSaplingBlock.class)
public class PlaceableBambooSoilFix {
    @SuppressWarnings("unused")
    @Unique
    private void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
    }

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void mysticmonstrosityfixes$requireSoil(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (!PlaceableBridge.loaded()) {
            return;
        }
        if (!level.getBlockState(pos.below()).is(BlockTags.BAMBOO_PLANTABLE_ON)) {
            ci.cancel();
        }
    }
}
