package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.google.common.collect.BiMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.RegistryManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Set;

/**
 * {@link ForgeRegistry#sync} rebuilds the target registry by calling {@code add} for every name in
 * the source registry, but it copies the source aliases first and leaves aliases that already exist
 * on the target in place. A stale alias whose source is also a real entry of the source registry can
 * therefore resolve that entry to the alias target while the target name map is only partially
 * rebuilt. {@code add} then takes the override path, reuses the target entry's id, and orphans the
 * original id. A later entry can resolve to that orphan and {@code getID} returns -1, which is passed
 * to {@code BitSet.set} and crashes the client on disconnect.
 * <p>
 * Keep every name that the incoming registry owns on its direct registration path for the duration
 * of the rebuild, and drop the server-provided aliases when reverting to the frozen snapshot so the
 * pre-join alias set is restored.
 */
@Mixin(value = ForgeRegistry.class, remap = false)
public abstract class ForgeRegistryAliasSyncFix {
    @Shadow
    @Final
    private BiMap<ResourceLocation, Object> names;

    @Shadow
    @Final
    private Map<ResourceLocation, ResourceLocation> aliases;

    @Unique
    private Set<ResourceLocation> mysticmonstrosityfixes$syncNames;

    @Inject(
            method = "sync(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraftforge/registries/ForgeRegistry;)V",
            at = @At("HEAD"),
            remap = false
    )
    private void mysticmonstrosityfixes$beginSync(
            ResourceLocation registryName, ForgeRegistry<?> from, CallbackInfo callback
    ) {
        if ((Object) this == from) {
            return;
        }
        if (RegistryManager.FROZEN.getRegistry(registryName) == from) {
            this.aliases.clear();
        }
        this.mysticmonstrosityfixes$syncNames = from.getKeys();
    }

    @Inject(
            method = "sync(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraftforge/registries/ForgeRegistry;)V",
            at = @At("TAIL"),
            remap = false
    )
    private void mysticmonstrosityfixes$endSync(
            ResourceLocation registryName, ForgeRegistry<?> from, CallbackInfo callback
    ) {
        this.mysticmonstrosityfixes$syncNames = null;
    }

    @Inject(method = "getRaw", at = @At("HEAD"), cancellable = true, remap = false)
    private void mysticmonstrosityfixes$keepSyncedNamesDirect(
            ResourceLocation key, CallbackInfoReturnable<Object> callback
    ) {
        Set<ResourceLocation> syncNames = this.mysticmonstrosityfixes$syncNames;
        if (syncNames != null && syncNames.contains(key)) {
            callback.setReturnValue(this.names.get(key));
        }
    }
}
