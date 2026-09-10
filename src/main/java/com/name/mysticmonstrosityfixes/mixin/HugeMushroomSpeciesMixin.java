package com.name.mysticmonstrosityfixes.mixin;

import com.ferreusveritas.dynamictrees.api.TreeHelper;
import com.ferreusveritas.dynamictrees.block.branch.BranchBlock;
import com.ferreusveritas.dynamictrees.block.leaves.DynamicLeavesBlock;
import com.ferreusveritas.dynamictrees.init.DTConfigs;
import com.ferreusveritas.dynamictrees.systems.genfeature.context.PostRotContext;
import com.ferreusveritas.dynamictrees.tree.species.Species;
import com.ferreusveritas.dynamictreesplus.tree.HugeMushroomSpecies;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = HugeMushroomSpecies.class, remap = false)
public abstract class HugeMushroomSpeciesMixin {

    /**
     * @author Mystic
     * @reason Use Dynamic Trees Species.rot implementation instead of the broken HugeMushroomSpecies override.
     */
    @Overwrite(remap = false)
    public boolean rot(LevelAccessor level, BlockPos pos, int neighborCount, int radius,
                       int fertility, RandomSource random, boolean rapid, boolean growLeaves) {

        Species self = (Species) (Object) this;
        SpeciesAccessor accessor = (SpeciesAccessor) (Object) this;

        if (!accessor.mmf$getDoesRot()) {
            return false;
        }

        if (radius <= self.getFamily().getPrimaryThickness()) {
            if (!self.getLeavesProperties().getDynamicLeavesBlock().isPresent()) {
                return false;
            }

            if (growLeaves) {
                final DynamicLeavesBlock leaves =
                        (DynamicLeavesBlock) self.getLeavesProperties()
                                .getDynamicLeavesState()
                                .getBlock();

                for (Direction dir : SpeciesAccessor.mmf$getUpFirst()) {
                    if (leaves.growLeavesIfLocationIsSuitable(
                            level,
                            self.getLeavesProperties(),
                            pos.relative(dir),
                            0)) {
                        return false;
                    }
                }
            }
        }

        if (rapid || (DTConfigs.MAX_BRANCH_ROT_RADIUS.get() != 0
                && radius <= DTConfigs.MAX_BRANCH_ROT_RADIUS.get())) {

            BranchBlock branch = TreeHelper.getBranch(level.getBlockState(pos));

            if (branch != null) {
                branch.rot(level, pos);
            }

            self.postRot(new PostRotContext(
                    level,
                    pos,
                    self,
                    radius,
                    neighborCount,
                    fertility,
                    rapid
            ));

            return true;
        }

        return false;
    }
}