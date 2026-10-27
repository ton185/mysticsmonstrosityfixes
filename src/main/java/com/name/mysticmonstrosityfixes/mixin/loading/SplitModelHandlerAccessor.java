package com.name.mysticmonstrosityfixes.mixin.loading;

import com.immersiveconvergence.api.client.split.SplitModelHandler;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** Registered split-model blocks. The values stay opaque; the bake fix only needs the block ids. */
@Mixin(value = SplitModelHandler.class, remap = false)
public interface SplitModelHandlerAccessor {
    @Accessor(value = "MACHINES", remap = false)
    static Map<ResourceLocation, Object> mysticmonstrosityfixes$machines() {
        throw new AssertionError("Mixin accessor was not applied.");
    }
}
