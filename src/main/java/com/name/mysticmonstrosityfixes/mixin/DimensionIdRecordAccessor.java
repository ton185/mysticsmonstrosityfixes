package com.name.mysticmonstrosityfixes.mixin;

import com.google.common.collect.BiMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import qouteall.q_misc_util.dimension.DimensionIdRecord;

@Mixin(DimensionIdRecord.class)
public interface DimensionIdRecordAccessor {
    @Accessor("idMap")
    BiMap<ResourceKey<Level>, Integer> getDimIdMap();
}