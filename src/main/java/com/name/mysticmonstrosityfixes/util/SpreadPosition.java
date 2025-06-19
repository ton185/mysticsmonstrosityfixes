package com.name.mysticmonstrosityfixes.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

public class SpreadPosition {
    public double x;
    public double z;

    public double dist(SpreadPosition other) {
        double dx = this.x - other.x;
        double dz = this.z - other.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public int getSpawnY(BlockGetter level, int maxY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(this.x, maxY + 1, this.z);
        boolean wasAir = level.getBlockState(pos).isAir();
        pos.move(Direction.DOWN);

        for (boolean currentAir = level.getBlockState(pos).isAir(); pos.getY() > level.getMinBuildHeight(); currentAir = level.getBlockState(pos).isAir()) {
            pos.move(Direction.DOWN);
            boolean nextAir = level.getBlockState(pos).isAir();
            if (!nextAir && currentAir && wasAir) {
                return pos.getY() + 1;
            }
            wasAir = currentAir;
        }

        return maxY + 1;
    }

    public boolean isSafe(BlockGetter level, int maxY) {
        int y = getSpawnY(level, maxY) - 1;
        BlockPos pos = BlockPos.containing(x, y, z);
        BlockState state = level.getBlockState(pos);
        return y < maxY && !state.liquid() && !state.is(BlockTags.FIRE);
    }
}
