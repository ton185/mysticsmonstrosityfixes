package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.name.mysticmonstrosityfixes.model.BakeEventModelView;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import org.embeddedt.modernfix.dynamicresources.DynamicBakedModelProvider;
import org.embeddedt.modernfix.forge.dynresources.ModelBakeEventHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

@Mixin(value = ModelBakeEventHelper.EmulatedModelRegistry.class, remap = false)
public abstract class ModernFixBakeEventIterationFix {
    @Shadow
    @Final
    private Set<ResourceLocation> ourModelLocations;

    @Shadow
    @Final
    private String modId;

    @Shadow
    protected abstract Map<ResourceLocation, BakedModel> delegate();

    @Inject(method = "entrySet", at = @At("RETURN"), cancellable = true, remap = false)
    private void mysticmonstrosityfixes$restrictEntrySet(
            CallbackInfoReturnable<Set<Map.Entry<ResourceLocation, BakedModel>>> callback
    ) {
        callback.setReturnValue(BakeEventModelView.entrySet(this.mysticmonstrosityfixes$registry(), this.mysticmonstrosityfixes$visibleLocations()));
    }

    @Inject(method = "keySet", at = @At("RETURN"), cancellable = true, remap = false)
    private void mysticmonstrosityfixes$restrictKeySet(
            CallbackInfoReturnable<Set<ResourceLocation>> callback
    ) {
        callback.setReturnValue(Collections.unmodifiableSet(
                BakeEventModelView.keySet(this.mysticmonstrosityfixes$registry(), this.mysticmonstrosityfixes$visibleLocations())
        ));
    }

    @Inject(method = "replaceAll", at = @At("HEAD"), cancellable = true, remap = false)
    private void mysticmonstrosityfixes$restrictReplaceAll(
            BiFunction<? super ResourceLocation, ? super BakedModel, ? extends BakedModel> function,
            CallbackInfo callback
    ) {
        Map<ResourceLocation, BakedModel> registry = this.mysticmonstrosityfixes$registry();
        for (ResourceLocation location : this.mysticmonstrosityfixes$visibleLocations()) {
            boolean needsReplacement;
            try {
                needsReplacement = function.apply(location, null) != null;
            } catch (Throwable ignored) {
                needsReplacement = true;
            }
            if (!needsReplacement) {
                continue;
            }
            BakedModel existing = registry.get(location);
            BakedModel replacement = function.apply(location, existing);
            if (replacement != existing) {
                registry.put(location, replacement);
            }
        }
        callback.cancel();
    }

    private Set<ResourceLocation> mysticmonstrosityfixes$visibleLocations() {
        Set<ResourceLocation> visible = new LinkedHashSet<>();
        for (ResourceLocation location : this.ourModelLocations) {
            if (this.modId.equals(location.getNamespace())) {
                visible.add(location);
            }
        }
        Map<ResourceLocation, BakedModel> registry = this.delegate();
        if (registry instanceof DynamicBakedModelProvider provider) {
            for (ResourceLocation location : provider.keySet()) {
                if (this.ourModelLocations.contains(location)) {
                    visible.add(location);
                }
            }
            Map<ResourceLocation, BakedModel> overrides =
                    provider instanceof ModernFixDynamicModelProviderAccessor accessor
                            ? accessor.mysticmonstrosityfixes$permanentOverrides()
                            : Map.of();
            synchronized (overrides) {
                for (ResourceLocation location : overrides.keySet()) {
                    if (this.ourModelLocations.contains(location)) {
                        visible.add(location);
                    }
                }
            }
        }
        return visible;
    }

    @SuppressWarnings("unchecked")
    private Map<ResourceLocation, BakedModel> mysticmonstrosityfixes$registry() {
        return (Map<ResourceLocation, BakedModel>) (Object) this;
    }
}
