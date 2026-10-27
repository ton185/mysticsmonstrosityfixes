package com.name.mysticmonstrosityfixes.mixin.loading;

import com.jykito.industrialcore.client.CoverModel;
import com.jykito.industrialcore.client.GlassCtmModel;
import com.name.mysticmonstrosityfixes.VoltexCoverHosts;
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
 * Applies Voltex's cover and structural-glass wrappers when ModernFix bakes the model, instead of
 * during the bake event.
 */
@Mixin(value = DynamicBakedModelProvider.class, remap = false)
public class VoltexDynamicCoverFix {
    @Shadow
    private Map<ResourceLocation, BakedModel> permanentOverrides;

    @Inject(
            method = "get(Ljava/lang/Object;)Lnet/minecraft/client/resources/model/BakedModel;",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private void mysticmonstrosityfixes$wrapVoltex(Object key, CallbackInfoReturnable<BakedModel> callback) {
        if (!(key instanceof ModelResourceLocation model) || "inventory".equals(model.getVariant())) {
            return;
        }
        BakedModel baked = callback.getReturnValue();
        if (baked == null || baked instanceof CoverModel || baked instanceof GlassCtmModel) {
            return;
        }
        String namespace = model.getNamespace();
        String path = model.getPath();
        BakedModel wrapped = null;
        if (VoltexCoverHosts.isGlass(namespace, path)) {
            wrapped = new GlassCtmModel(baked);
        } else if (VoltexCoverHosts.isCover(namespace, path)) {
            wrapped = new CoverModel(baked);
        }
        if (wrapped == null) {
            return;
        }
        this.permanentOverrides.put(model, wrapped);
        callback.setReturnValue(wrapped);
    }
}
