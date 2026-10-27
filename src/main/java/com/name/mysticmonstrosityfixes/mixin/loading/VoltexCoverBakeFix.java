package com.name.mysticmonstrosityfixes.mixin.loading;

import com.jykito.industrialcore.client.ClientSetup;
import com.name.mysticmonstrosityfixes.VoltexCoverHosts;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ModelEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Voltex wraps every coverable block state by looking it up in the bake registry. With ModernFix
 * that lookup bakes the model. This boot that was 18.92 seconds. Record the blocks and let
 * {@link VoltexDynamicCoverFix} wrap a model the first time it is requested.
 */
@Mixin(value = ClientSetup.class, remap = false)
public class VoltexCoverBakeFix {
    @Inject(method = "onModifyBakingResult", at = @At("HEAD"), cancellable = true, remap = false)
    private static void mysticmonstrosityfixes$deferCoverBake(ModelEvent.ModifyBakingResult event, CallbackInfo ci) {
        VoltexCoverHosts.rebuild();
        Map<ResourceLocation, BakedModel> models = event.getModels();
        if (models.getClass().getName().startsWith("org.embeddedt.modernfix.")) {
            ci.cancel();
        }
    }
}
