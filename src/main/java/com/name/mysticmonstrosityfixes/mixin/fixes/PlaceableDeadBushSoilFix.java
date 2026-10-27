package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.name.mysticmonstrosityfixes.placeable.PlaceableBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.DeadBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Same shared-refmap failure as {@link PlaceableSaplingSoilFix}, for DeadBushBlock.mayPlaceOn.
 */
@Mixin(DeadBushBlock.class)
public class PlaceableDeadBushSoilFix {
    @SuppressWarnings("unused")
    @Unique
    private boolean mayPlaceOn(BlockState floor, BlockGetter level, BlockPos pos) {
        return false;
    }

    @Inject(method = "mayPlaceOn", at = @At("HEAD"), cancellable = true)
    private void mysticmonstrosityfixes$expandedPlacement(BlockState floor, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Boolean> ci) {
        if (!PlaceableBridge.loaded()) {
            return;
        }
        BlockState state = ((DeadBushBlock) (Object) this).defaultBlockState();
        if (PlaceableBridge.shouldSkip(state, level)) {
            return;
        }
        if (PlaceableBridge.isValidFloor(floor, level, pos)) {
            ci.setReturnValue(true);
        }
    }
}
