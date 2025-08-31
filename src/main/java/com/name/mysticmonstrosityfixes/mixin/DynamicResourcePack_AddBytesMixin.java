package com.name.mysticmonstrosityfixes.mixin;

import com.name.mysticmonstrosityfixes.MoonlightAssetsCache;
import net.mehvahdjukaar.moonlight.api.resources.pack.DynamicResourcePack;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DynamicResourcePack.class, remap = false)
public abstract class DynamicResourcePack_AddBytesMixin {

    @Inject(method = "addBytes*", at = @At("TAIL"))
    private void mysticmonstrosityfixes$stageForCache(ResourceLocation id, byte[] bytes, CallbackInfo ci) {
        MoonlightAssetsCache.put(id, bytes);
    }
}
