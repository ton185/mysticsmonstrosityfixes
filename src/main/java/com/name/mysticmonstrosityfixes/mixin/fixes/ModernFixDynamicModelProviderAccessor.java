package com.name.mysticmonstrosityfixes.mixin.fixes;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import org.embeddedt.modernfix.dynamicresources.DynamicBakedModelProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(value = DynamicBakedModelProvider.class, remap = false)
public interface ModernFixDynamicModelProviderAccessor {
    @Accessor(value = "permanentOverrides", remap = false)
    Map<ResourceLocation, BakedModel> mysticmonstrosityfixes$permanentOverrides();
}
