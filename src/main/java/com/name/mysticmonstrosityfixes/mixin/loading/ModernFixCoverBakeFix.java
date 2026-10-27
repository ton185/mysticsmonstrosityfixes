package com.name.mysticmonstrosityfixes.mixin.loading;

import com.faktocraft.client.model.CoverBakedModel;
import com.name.mysticmonstrosityfixes.FaktocraftCoverHosts;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import org.embeddedt.modernfix.dynamicresources.DynamicBakedModelProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * ModernFix bakes a top-level model the first time it is looked up, so the bake event's key set
 * is not the set of block models Faktocraft needs to wrap. Wrap a coverable block on that first
 * lookup and keep the wrapped model for the next one.
 */
@Mixin(value = DynamicBakedModelProvider.class, remap = false)
public class ModernFixCoverBakeFix {
    @Shadow
    private Map<ResourceLocation, BakedModel> permanentOverrides;

    @Inject(
            method = "get(Ljava/lang/Object;)Lnet/minecraft/client/resources/model/BakedModel;",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private void mysticmonstrosityfixes$wrapCover(Object key, CallbackInfoReturnable<BakedModel> callback) {
        if (!(key instanceof ModelResourceLocation model) || "inventory".equals(model.getVariant())) {
            return;
        }
        BakedModel baked = callback.getReturnValue();
        if (baked == null || baked instanceof CoverBakedModel) {
            return;
        }
        Boolean drilled = FaktocraftCoverHosts.drilledFlag(model.getNamespace(), model.getPath());
        if (drilled == null) {
            return;
        }
        CoverBakedModel wrapped = new CoverBakedModel(baked, drilled);
        this.permanentOverrides.put(model, wrapped);
        callback.setReturnValue(wrapped);
    }
}
