package com.name.mysticmonstrosityfixes.mixin;

import com.google.common.collect.BiMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.q_misc_util.dimension.DimensionIdManagement;
import qouteall.q_misc_util.dimension.DimensionIdRecord;

@Mixin(DimensionIdManagement.class)
public abstract class DimensionIdManagementMixin {
    @Unique
    private static final ResourceKey<Level> AE2_SPATIAL_STORAGE = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("ae2", "spatial_storage")
    );

    @Inject(method = "completeServerIdRecord", at = @At("TAIL"), remap = false)
    private static void injectAe2SpatialStorage(CallbackInfo ci) {
        DimensionIdRecord record = DimensionIdRecord.serverRecord;
        if (record != null) {
            BiMap<ResourceKey<Level>, Integer> idMap = ((DimensionIdRecordAccessor)record).getDimIdMap();
            if (!idMap.containsKey(AE2_SPATIAL_STORAGE)) {
                int nextId = idMap.values().stream()
                        .mapToInt(Integer::intValue)
                        .max()
                        .orElse(1) + 1;
                idMap.put(AE2_SPATIAL_STORAGE, nextId);
            }
        }
    }
}