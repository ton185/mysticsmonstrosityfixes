package com.name.mysticmonstrosityfixes.mixin;

import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceCollection;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegionManager;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Workaround for NPE inside fastutil's Long2ReferenceOpenHashMap iterator
 * during Embeddium/Sodium region updates. We replace the live values()
 * view with a snapshot list to prevent the MapIterator path from being used.
 */
@Mixin(value = RenderRegionManager.class, remap = false)
public abstract class ImmersivePortalSodiumFix {
    @Shadow @Final
    private Long2ReferenceOpenHashMap<RenderRegion> regions;

    /**
     * Redirect the call to Long2ReferenceOpenHashMap#values() inside RenderRegionManager#update(...)
     * so that, instead of returning the live ObjectCollection view (which uses the brittle MapIterator),
     * we return a plain ObjectList snapshot that is safe to iterate even if the map mutates.
     * <p>
     * Target descriptor matches:
     * values()Lit/unimi/dsi/fastutil/objects/ObjectCollection;
     */
    @Redirect(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Lit/unimi/dsi/fastutil/longs/Long2ReferenceOpenHashMap;values()Lit/unimi/dsi/fastutil/objects/ReferenceCollection;"
            )
    )
    private ReferenceCollection<RenderRegion>
    mysticmonstrosityfixes$snapshotValues_Ref(Long2ReferenceOpenHashMap<RenderRegion> map) {

        ReferenceArrayList<RenderRegion> snapshot =
                new ReferenceArrayList<>(map.size());

        for (RenderRegion r : map.values()) {
            if (r != null) snapshot.add(r);
        }
        return snapshot;
    }

}
