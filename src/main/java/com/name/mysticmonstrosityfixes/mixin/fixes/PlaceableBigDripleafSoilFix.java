package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.name.mysticmonstrosityfixes.placeable.PlaceableBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BigDripleafBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Same shared-refmap failure as {@link PlaceableSaplingSoilFix}, for BigDripleafBlock.canSurvive.
 */
@Mixin(BigDripleafBlock.class)
public class PlaceableBigDripleafSoilFix {
    @SuppressWarnings("unused")
    @Unique
    private boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return false;
    }

    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void mysticmonstrosityfixes$expandedPlacement(BlockState state, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> ci) {
        if (!PlaceableBridge.loaded() || PlaceableBridge.shouldSkip(state, level)) {
            return;
        }
        if (PlaceableBridge.isValidFloor(level, pos)) {
            ci.setReturnValue(true);
        }
    }
}
