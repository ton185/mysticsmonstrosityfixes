package com.name.mysticmonstrosityfixes.mixin;

import com.name.mysticmonstrosityfixes.MoonlightAssetsCache;
import net.mehvahdjukaar.moonlight.api.misc.IProgressTracker;
import net.mehvahdjukaar.moonlight.api.resources.pack.DynResourceGenerator;
import net.mehvahdjukaar.moonlight.api.resources.pack.DynamicResourcePack;
import net.mehvahdjukaar.moonlight.core.Moonlight;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Mixin(value = DynResourceGenerator.class, remap = false)
public abstract class DynResourceGenerator_CacheMixin {

    // --- fields we need from the target ---
    @Shadow private boolean hasBeenInitialized;
    @Shadow @Final protected DynamicResourcePack dynamicPack;

    @Shadow protected abstract boolean runsOnEveryReload();
    @Shadow protected abstract void regenerateDynamicAssets(ResourceManager manager, IProgressTracker reporter);
    
    @Unique private boolean mysticmonstrosityfixes$firstReload;
    @Unique private boolean mysticmonstrosityfixes$cacheValid;

    @Inject(method = "reloadResources", at = @At("HEAD"))
    private void mysticmonstrosityfixes$begin(ResourceManager manager, IProgressTracker reporter, CallbackInfo ci) {
        mysticmonstrosityfixes$firstReload = !this.hasBeenInitialized;

        String mcVer = net.minecraft.SharedConstants.getCurrentVersion().getName();
        String moonVer = "2.16.8";
        Map<String,String> mods = new LinkedHashMap<>();
        String cfg = "default";

        try {
            MoonlightAssetsCache.begin(mcVer, moonVer, mods, cfg);
        } catch (IOException e) {
            System.err.println("[Moonlight Cache MM Fixes] cache begin failed: " + e);
        }
    }

    /**
     * Redirect the actual heavy call. If cache is valid AND this isn't a forced run (runsOnEveryReload)
     * AND it's not the first ever reload, we skip regeneration.
     *
     * This matches the call-site inside:
     *   if (this.runsOnEveryReload() || wasFirstReload) { ... regenerateDynamicAssets(manager, reporter); }
     */
    @Redirect(
        method = "reloadResources",
        at = @At(
            value = "INVOKE",
            target = "Lnet/mehvahdjukaar/moonlight/api/resources/pack/DynResourceGenerator;regenerateDynamicAssets(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/mehvahdjukaar/moonlight/api/misc/IProgressTracker;)V"
        )
    )
    private void mysticmonstrosityfixes$maybeSkipRegen(DynResourceGenerator self,
                                        ResourceManager manager,
                                        IProgressTracker reporter) {
        if (!mysticmonstrosityfixes$cacheValid) {
            mysticmonstrosityfixes$cacheValid = MoonlightAssetsCache.tryWarmStart((id, bytes) -> {
                ((DynamicResourcePack_AddBytesInvoker) this.dynamicPack).mysticmonstrosityfixes$invokeAddBytes(id, bytes);
            });
        }

        if (mysticmonstrosityfixes$cacheValid && !this.runsOnEveryReload() && !mysticmonstrosityfixes$firstReload) {
            return;
        }

        this.regenerateDynamicAssets(manager, reporter);
    }

    @Inject(method = "reloadResources", at = @At("RETURN"))
    private void mysticmonstrosityfixes$firstReload$persist(ResourceManager manager, IProgressTracker reporter, CallbackInfo ci) {
        MoonlightAssetsCache.persist();
    }
}
