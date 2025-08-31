package com.name.mysticmonstrosityfixes.mixin;

import net.mehvahdjukaar.moonlight.api.resources.pack.DynamicResourcePack;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = DynamicResourcePack.class, remap = false)
public interface DynamicResourcePack_AddBytesInvoker {
    @Invoker("addBytes")
    void mysticmonstrosityfixes$invokeAddBytes(ResourceLocation id, byte[] bytes);
}
