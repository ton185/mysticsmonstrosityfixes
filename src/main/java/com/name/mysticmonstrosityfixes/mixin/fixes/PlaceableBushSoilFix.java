package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.name.mysticmonstrosityfixes.placeable.PlaceableBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Same shared-refmap failure as {@link PlaceableSaplingSoilFix}, for BushBlock.canSurvive.
 */
@Mixin(BushBlock.class)
public class PlaceableBushSoilFix {
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
        Object self = this;
        if (self instanceof CropBlock || self instanceof NetherWartBlock || self instanceof SweetBerryBushBlock || self instanceof SaplingBlock) {
            if (!PlaceableBridge.isAdditionalPlant(state)) {
                return;
            }
        }
        if (PlaceableBridge.isValidFloor(level, pos)) {
            ci.setReturnValue(true);
        }
    }
}
